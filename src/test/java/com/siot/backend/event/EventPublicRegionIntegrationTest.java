package com.siot.backend.event;

import com.siot.backend.TestcontainersConfiguration;
import com.siot.backend.auth.service.SignupVerificationService;
import com.siot.backend.rag.dto.RelatedPage;
import com.siot.backend.rag.service.EventRelatedPageFinder;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 사용자에게는 공개 지역(기본값 SIHEUNG)의 행사만 보이고, 관리자 API 는 모든 지역을 본다. 오늘 = 2026-11-20 */
@SpringBootTest(properties = "siot.scheduling.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EventPublicRegionIntegrationTest {

    private static final String PASSWORD = "abcd1234";

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-11-20T03:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    StringRedisTemplate redisTemplate;
    @Autowired
    EventRelatedPageFinder relatedPageFinder;

    private Cookie member;
    private long memberId;
    private Cookie admin;
    private long siheung;
    private long gyeonggi;
    private long pastSiheung;
    private long pastGyeonggi;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("DELETE FROM event");

        String memberEmail = newEmail();
        member = signup(memberEmail);
        memberId = jdbc.queryForObject("SELECT user_id FROM app_user WHERE email = ?", Long.class, memberEmail);
        String adminEmail = newEmail();
        signup(adminEmail);
        jdbc.update("UPDATE app_user SET role = 'ADMIN' WHERE email = ?", adminEmail);
        admin = login(adminEmail);

        // 경기도 행사를 날짜가 더 빠르게 두어, 걸러지지 않으면 맨 앞에 나오게 한다
        gyeonggi = event("g", "경기 축제", "2026-11-21", "GYEONGGI");
        siheung = event("s", "시흥 축제", "2026-11-25", "SIHEUNG");
        pastGyeonggi = event("pg", "지난 경기 축제", "2026-11-10", "GYEONGGI");
        pastSiheung = event("ps", "지난 시흥 축제", "2026-11-09", "SIHEUNG");
    }

    @Test
    void listCalendarAndRecommendationsShowOnlySiheung() throws Exception {
        mockMvc.perform(get("/api/events").cookie(member))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[*].eventId").value(contains(id(siheung))));
        mockMvc.perform(get("/api/events").param("past", "true").cookie(member))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[*].eventId").value(contains(id(pastSiheung))));
        mockMvc.perform(get("/api/events").param("region", "SIHEUNG").cookie(member))
                .andExpect(jsonPath("$.data.content[*].eventId").value(contains(id(siheung))));
        mockMvc.perform(get("/api/events/calendar").param("year", "2026").param("month", "11").cookie(member))
                .andExpect(jsonPath("$.data[*].eventId").value(contains(id(pastSiheung), id(siheung))));
        mockMvc.perform(get("/api/events/recommendations").param("size", "10").cookie(member))
                .andExpect(jsonPath("$.data[*].event.eventId").value(contains(id(siheung))));
    }

    @Test
    void askingForHiddenRegionReturnsEmptyAndUnknownRegionIsRejected() throws Exception {
        for (String past : new String[]{"false", "true"}) {
            mockMvc.perform(get("/api/events").param("region", "GYEONGGI").param("past", past).cookie(member))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(0))
                    .andExpect(jsonPath("$.data.content").isEmpty());
        }
        mockMvc.perform(get("/api/events").param("region", "SEOUL").cookie(member))
                .andExpect(status().isBadRequest());
    }

    @Test
    void hiddenEventCannotBeOpenedSubscribedOrReported() throws Exception {
        mockMvc.perform(get("/api/events/" + siheung).cookie(member)).andExpect(status().isOk());
        mockMvc.perform(get("/api/events/" + gyeonggi).cookie(member))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
        mockMvc.perform(post("/api/events/" + gyeonggi + "/subscriptions").with(csrf()).cookie(member))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
        mockMvc.perform(post("/api/reports").with(csrf()).cookie(member).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\": " + gyeonggi + ", \"quotedText\": \"장소\", \"reason\": \"CONTENT\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
    }

    /** 지역 제한 전에 신청한 경기도 행사: 목록·신청 여부에서 숨기고, 해제는 된다. 신청 기록은 남는다 */
    @Test
    void existingSubscriptionToHiddenEventIsHiddenButCanBeCancelled() throws Exception {
        subscribe(gyeonggi);
        subscribe(siheung);

        mockMvc.perform(get("/api/members/me/event-subscriptions").cookie(member))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[*].eventId").value(contains(id(siheung))));
        mockMvc.perform(get("/api/events/" + gyeonggi + "/subscriptions").cookie(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscribed").value(false))
                .andExpect(jsonPath("$.data.leadDays").value(nullValue()));
        mockMvc.perform(get("/api/events/" + siheung + "/subscriptions").cookie(member))
                .andExpect(jsonPath("$.data.subscribed").value(true));
        assertThat(jdbc.queryForObject("SELECT enabled FROM event_subscription WHERE event_id = ?", Boolean.class,
                gyeonggi)).isTrue();

        mockMvc.perform(delete("/api/events/" + gyeonggi + "/subscriptions").with(csrf()).cookie(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));
    }

    @Test
    void chatRelatedPagesLinkOnlySiheungEvents() {
        assertThat(relatedPageFinder.find("축제 행사 알려 주세요", "ko", 10))
                .extracting(RelatedPage::code)
                .containsExactly(String.valueOf(siheung));
    }

    @Test
    void adminSeesAllRegionsAndMovingAnEventToSiheungPublishesIt() throws Exception {
        mockMvc.perform(get("/api/admin/events").cookie(admin))
                .andExpect(jsonPath("$.data.content[*].eventId")
                        .value(containsInAnyOrder(id(siheung), id(gyeonggi), id(pastSiheung), id(pastGyeonggi))));
        mockMvc.perform(get("/api/admin/events").param("region", "GYEONGGI").cookie(admin))
                .andExpect(jsonPath("$.data.totalElements").value(2));
        mockMvc.perform(get("/api/admin/events/" + gyeonggi).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.region").value("GYEONGGI"));

        // 경기도로 잘못 분류된 시흥 행사는 관리자가 지역을 고치면 보인다
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/admin/events/" + gyeonggi).with(csrf()).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"region\": \"SIHEUNG\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/events/" + gyeonggi).cookie(member)).andExpect(status().isOk());
    }

    // ================================================================ 도우미

    private long event(String externalId, String title, String date, String region) {
        Long eventId = jdbc.queryForObject("INSERT INTO event (external_source, external_id, region_code, starts_on, "
                        + "ends_on, time_is_unknown) VALUES ('GGC', ?, ?, ?::date, ?::date, TRUE) RETURNING event_id",
                Long.class, externalId, region, date, date);
        jdbc.update("INSERT INTO event_translation (language, event_id, title, source_revision) VALUES ('ko', ?, ?, 1)",
                eventId, title);
        return eventId;
    }

    private void subscribe(long eventId) {
        jdbc.update("INSERT INTO event_subscription (user_id, event_id, enabled) VALUES (?, ?, TRUE)", memberId,
                eventId);
    }

    /** JSON 숫자 비교용 (jsonPath 는 작은 수를 Integer 로 읽는다) */
    private static Object id(long id) {
        return Math.toIntExact(id);
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

    private static String newEmail() {
        return "pr" + ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L) + "@example.com";
    }
}
