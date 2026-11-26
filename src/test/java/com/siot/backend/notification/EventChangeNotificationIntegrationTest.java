package com.siot.backend.notification;

import com.siot.backend.TestcontainersConfiguration;
import com.siot.backend.auth.service.SignupVerificationService;
import com.siot.backend.notification.scheduler.NotificationScheduler;
import com.siot.backend.notification.service.EventChangeNotificationWriter;
import com.siot.backend.translation.Translator;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** NTF-02 행사 일정 변경 즉시 알림 (관리자 수정 커밋 뒤) 과 매시 보완 경로 (#15) */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EventChangeNotificationIntegrationTest {

    private static final String PASSWORD = "abcd1234";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    StringRedisTemplate redisTemplate;
    @Autowired
    NotificationScheduler notificationScheduler;

    @MockitoBean
    Translator translator;
    @MockitoSpyBean
    EventChangeNotificationWriter writer;

    private Cookie admin;
    private long eventId;

    @BeforeEach
    void setUp() throws Exception {
        reset(writer);
        when(translator.translateAll(anyString())).thenReturn(Map.of());
        String adminEmail = newEmail();
        signup(adminEmail);
        jdbc.update("UPDATE app_user SET role = 'ADMIN' WHERE email = ?", adminEmail);
        admin = login(adminEmail);
        eventId = jdbc.queryForObject("INSERT INTO event (external_source, external_id, region_code, starts_on, ends_on, "
                + "time_is_unknown) VALUES ('GGC', ?, 'SIHEUNG', '2099-06-10', '2099-06-10', TRUE) RETURNING event_id",
                Long.class, UUID.randomUUID().toString());
        jdbc.update("INSERT INTO event_translation (language, event_id, title, source_revision) VALUES ('ko', ?, ?, 1)",
                eventId, "시흥 합창 공연");
    }

    @Test
    void adminScheduleEditNotifiesSubscribersAtOnceAndOnlyOnce() throws Exception {
        long subscriber = subscribedMember();
        long optedOut = subscribedMember();
        jdbc.update("INSERT INTO notification_preference (kind, user_id, enabled, lead_days) VALUES ('EVENT', ?, false, 1)",
                optedOut);

        patchEvent("{\"startsOn\": \"2099-06-11\", \"endsOn\": \"2099-06-11\"}");

        // 커밋 직후, 매시 작업 전에 이미 1건
        assertThat(changeNotifications(subscriber)).isEqualTo(1);
        assertThat(changeNotifications(optedOut)).isZero();
        assertThat(jdbc.queryForObject("SELECT schedule_snapshot FROM event_subscription WHERE user_id = ? AND event_id = ?",
                String.class, subscriber, eventId)).isEqualTo("2099-06-11|2099-06-11||");

        // 매시 보완 경로는 같은 변경을 다시 보내지 않는다
        notificationScheduler.generateEventNotifications();
        assertThat(changeNotifications(subscriber)).isEqualTo(1);

        // 일정 외 항목만 바꾸면 알림 없음
        patchEvent("{\"venue\": \"시흥ABC행복학습타운\"}");
        assertThat(changeNotifications(subscriber)).isEqualTo(1);

        // 다시 바꾸면 새 일정으로 1건 더
        patchEvent("{\"startsOn\": \"2099-06-12\", \"endsOn\": \"2099-06-12\"}");
        assertThat(changeNotifications(subscriber)).isEqualTo(2);
    }

    @Test
    void subscriptionWithoutRecordedScheduleIsNotifiedByImmediatePath() throws Exception {
        long subscriber = subscribedMember();
        // 기능 전 신청 (기록 없음)
        jdbc.update("UPDATE event_subscription SET schedule_snapshot = NULL WHERE user_id = ?", subscriber);

        patchEvent("{\"startsOn\": \"2099-06-11\", \"endsOn\": \"2099-06-11\"}");

        assertThat(changeNotifications(subscriber)).isEqualTo(1);
    }

    @Test
    void notificationFailureDoesNotFailTheEdit() throws Exception {
        long subscriber = subscribedMember();
        doThrow(new IllegalStateException("boom")).when(writer).apply(any(), any(), any());

        patchEvent("{\"startsOn\": \"2099-06-11\", \"endsOn\": \"2099-06-11\"}");

        assertThat(jdbc.queryForObject("SELECT starts_on::text FROM event WHERE event_id = ?", String.class, eventId))
                .isEqualTo("2099-06-11");
        assertThat(changeNotifications(subscriber)).isZero();
    }

    private void patchEvent(String body) throws Exception {
        mockMvc.perform(patch("/api/admin/events/" + eventId).with(csrf()).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private int changeNotifications(long userId) {
        return jdbc.queryForObject("SELECT count(*) FROM notification WHERE user_id = ? AND event_id = ? "
                + "AND source_key LIKE 'event_changed:%'", Integer.class, userId, eventId);
    }

    private long subscribedMember() throws Exception {
        String email = newEmail();
        Cookie member = signup(email);
        mockMvc.perform(post("/api/events/" + eventId + "/subscriptions").with(csrf()).cookie(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true));
        return jdbc.queryForObject("SELECT user_id FROM app_user WHERE email = ?", Long.class, email);
    }

    private static String newEmail() {
        return "evt-change-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    private Cookie signup(String email) throws Exception {
        redisTemplate.opsForValue().set(SignupVerificationService.VERIFIED_KEY + email, "1");
        MvcResult result = mockMvc.perform(post("/api/auth/signup").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                                + "\",\"passwordConfirm\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return result.getResponse().getCookie("SIOT_SESSION");
    }

    private Cookie login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie("SIOT_SESSION");
    }
}
