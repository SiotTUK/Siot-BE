package com.siot.backend.translation.bill;

import com.siot.backend.TestcontainersConfiguration;
import com.siot.backend.auth.service.SignupVerificationService;
import com.siot.backend.translation.BillReader;
import com.siot.backend.translation.BillReading;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** RUL-07 최근 번역 저장·조회·삭제와 납부 마감 알림 (BillReader 모킹, DB·Redis 는 Testcontainers) */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BillTranslationHistoryIntegrationTest {

    private static final String EMAIL = "bill-history@example.com";
    private static final String OTHER_EMAIL = "bill-history-other@example.com";
    private static final String PASSWORD = "abcd1234";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    BillDueNotifier billDueNotifier;

    @Autowired
    BillTranslationHistoryService historyService;

    @Autowired
    Clock clock;

    @MockitoBean
    BillReader billReader;

    private Cookie member;
    private long memberId;
    private LocalDate today;

    @BeforeEach
    void setUp() throws Exception {
        reset(billReader);
        member = login(EMAIL);
        memberId = userId(EMAIL);
        today = LocalDate.now(clock);
        jdbcTemplate.update("DELETE FROM notification WHERE user_id = ?", memberId);
        jdbcTemplate.update("DELETE FROM bill_translation WHERE user_id = ?", memberId);
        jdbcTemplate.update("DELETE FROM notification_preference WHERE user_id = ?", memberId);
        jdbcTemplate.update("UPDATE app_user SET language = 'en' WHERE user_id = ?", memberId);
        clearRateLimit(memberId);
    }

    @Test
    void savesReadableResultAndListsIt() throws Exception {
        readBill(today.plusDays(20));

        MvcResult created = mockMvc.perform(multipart("/api/translations/bills").file(jpeg()).param("consent", "true")
                        .cookie(member).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.reminder").value(false))
                .andExpect(jsonPath("$.data.savedAt").isNotEmpty())
                .andReturn();
        long id = idOf(created);

        // 사진은 저장하지 않는다: 결과 JSON 에도 원본 차량번호가 없다
        String stored = jdbcTemplate.queryForObject(
                "SELECT result FROM bill_translation WHERE bill_translation_id = ?", String.class, id);
        assertThat(stored).doesNotContain("12가3456").contains("12가****");

        mockMvc.perform(get("/api/translations/bills").cookie(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(id))
                .andExpect(jsonPath("$.data[0].taxType").value("자동차세"))
                .andExpect(jsonPath("$.data[0].what").value("Tax notice - 자동차세 (시흥시)"))
                .andExpect(jsonPath("$.data[0].dueDate").value(today.plusDays(20).toString()))
                .andExpect(jsonPath("$.data[0].reminder").value(false))
                .andExpect(jsonPath("$.data[0].reminderAvailable").value(true));

        mockMvc.perform(get("/api/translations/bills/" + id).cookie(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.taxObject").value("12가****"))
                .andExpect(jsonPath("$.data.summary.what").value("Tax notice - 자동차세 (시흥시)"))
                .andExpect(jsonPath("$.data.relatedGuides[0].code").value("CL-10"));
    }

    @Test
    void unreadableResultIsNotSaved() throws Exception {
        when(billReader.read(any(), anyString(), anyString())).thenReturn(BillReading.unreadable("BLURRY"));

        mockMvc.perform(multipart("/api/translations/bills").file(jpeg()).param("consent", "true")
                        .param("remind", "true").cookie(member).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readable").value(false))
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.reminder").value(false));

        assertThat(count("SELECT count(*) FROM bill_translation WHERE user_id = ?", memberId)).isZero();
    }

    @Test
    void remindAtUploadOnlyWhenDueDateIsAhead() throws Exception {
        readBill(today.plusDays(7));
        mockMvc.perform(multipart("/api/translations/bills").file(jpeg()).param("consent", "true")
                        .param("remind", "true").cookie(member).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reminder").value(true));

        clearRateLimit(memberId);
        readBill(today.minusDays(1));
        mockMvc.perform(multipart("/api/translations/bills").file(jpeg()).param("consent", "true")
                        .param("remind", "true").cookie(member).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readable").value(true))
                .andExpect(jsonPath("$.data.reminder").value(false));
    }

    @Test
    void reminderCanBeTurnedOnAndOff() throws Exception {
        long id = translate(today.plusDays(10));

        mockMvc.perform(put("/api/translations/bills/" + id + "/reminder").cookie(member).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reminder").value(true));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT reminder FROM bill_translation WHERE bill_translation_id = ?", Boolean.class, id)).isTrue();

        mockMvc.perform(put("/api/translations/bills/" + id + "/reminder").cookie(member).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reminder").value(false));

        mockMvc.perform(put("/api/translations/bills/" + id + "/reminder").cookie(member).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reminderNeedsDueDateAhead() throws Exception {
        long past = translate(today.minusDays(3));
        long noDue = translate(null);

        for (long id : List.of(past, noDue)) {
            mockMvc.perform(put("/api/translations/bills/" + id + "/reminder").cookie(member).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BILL_REMINDER_UNAVAILABLE"));
        }
    }

    @Test
    void otherMembersCannotSeeOrChangeIt() throws Exception {
        long id = translate(today.plusDays(10));
        Cookie other = login(OTHER_EMAIL);

        mockMvc.perform(get("/api/translations/bills/" + id).cookie(other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BILL_TRANSLATION_NOT_FOUND"));
        mockMvc.perform(put("/api/translations/bills/" + id + "/reminder").cookie(other).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/translations/bills/" + id).cookie(other).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/translations/bills").cookie(other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(get("/api/translations/bills"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void keepsOnlyTenRecentButKeepsPendingReminders() throws Exception {
        BillTranslationResponse reminded = historyService.save(memberId, readable(today.plusDays(30)), true);
        for (int i = 0; i < BillTranslationHistoryService.MAX_RECENT + 2; i++) {
            historyService.save(memberId, readable(today.plusDays(5)), false);
        }

        // 최근 10개 + 아직 알림이 남은 오래된 1개
        assertThat(count("SELECT count(*) FROM bill_translation WHERE user_id = ?", memberId))
                .isEqualTo(BillTranslationHistoryService.MAX_RECENT + 1);
        assertThat(count("SELECT count(*) FROM bill_translation WHERE bill_translation_id = ?", reminded.id()))
                .isEqualTo(1);
        mockMvc.perform(get("/api/translations/bills").cookie(member))
                .andExpect(jsonPath("$.data.length()").value(BillTranslationHistoryService.MAX_RECENT));
    }

    @Test
    void dueNotificationsAtSevenAndOneDayBefore() throws Exception {
        long sevenDays = translate(today.plusDays(7));
        long oneDay = translate(today.plusDays(1));
        long notReminded = translate(today.plusDays(7));
        long otherDay = translate(today.plusDays(3));
        for (long id : List.of(sevenDays, oneDay, otherDay)) {
            historyService.changeReminder(memberId, id, true);
        }

        billDueNotifier.generate();
        billDueNotifier.generate(); // 두 번 돌아도 한 번만

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT kind, bill_translation_id, title, body, locale FROM notification WHERE user_id = ? "
                        + "ORDER BY bill_translation_id", memberId);
        assertThat(rows).extracting(row -> ((Number) row.get("bill_translation_id")).longValue())
                .containsExactly(sevenDays, oneDay);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.get("kind")).isEqualTo("BILL_DUE");
            assertThat(row.get("locale")).isEqualTo("en");
        });
        assertThat((String) rows.get(0).get("title")).isEqualTo("[자동차세] Payment due in 7 day(s)");
        assertThat((String) rows.get(0).get("body")).contains(today.plusDays(7).toString()).contains("123,450 won");
        assertThat(notReminded).isPositive();

        // 알림 목록에서 번역 결과로 간다
        mockMvc.perform(get("/api/members/me/notifications").cookie(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].kind").value("BILL_DUE"))
                .andExpect(jsonPath("$.data.content[0].linkType").value("BILL_TRANSLATION"));

        // 번역을 지우면 알림은 남고 연결만 끊긴다
        mockMvc.perform(delete("/api/translations/bills/" + sevenDays).cookie(member).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(count("SELECT count(*) FROM notification WHERE user_id = ? AND kind = 'BILL_DUE'", memberId))
                .isEqualTo(2);
        assertThat(count("SELECT count(*) FROM notification WHERE user_id = ? AND bill_translation_id IS NULL",
                memberId)).isEqualTo(1);
    }

    @Test
    void dueNotificationsFollowTaxPreference() throws Exception {
        long id = translate(today.plusDays(7));
        historyService.changeReminder(memberId, id, true);
        jdbcTemplate.update("INSERT INTO notification_preference (kind, user_id, enabled, lead_days) "
                + "VALUES ('TAX', ?, false, 7)", memberId);

        billDueNotifier.generate();

        assertThat(count("SELECT count(*) FROM notification WHERE user_id = ?", memberId)).isZero();
    }

    private long translate(LocalDate dueDate) throws Exception {
        clearRateLimit(memberId);
        readBill(dueDate);
        MvcResult result = mockMvc.perform(multipart("/api/translations/bills").file(jpeg()).param("consent", "true")
                        .cookie(member).with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        return idOf(result);
    }

    private void readBill(LocalDate dueDate) {
        when(billReader.read(any(), anyString(), anyString())).thenReturn(new BillReading(true, null,
                "TAX_NOTICE", "시흥시", "자동차세", "12가3456", 123450L, dueDate, null, null, null,
                List.of("Wetax (위택스)"), "031-310-2114", List.of("납기내 금액 123,450원"),
                new BillReading.Summary("Car tax notice", "Pay by the due date.", "Pay 123,450 won.", "Wetax")));
    }

    private static BillTranslationResponse readable(LocalDate dueDate) {
        return new BillTranslationResponse("en", true, null, "TAX_NOTICE", "시흥시", "자동차세", "12가****", 1000L,
                dueDate, null, null, null, List.of(), null, List.of(),
                new BillTranslationResponse.Summary("what", "due", "amount", "how"), "AI", List.of(), List.of(),
                null, null, false, 1, null, false, null);
    }

    private long idOf(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\"id\":(\\d+)").matcher(body);
        assertThat(matcher.find()).as(body).isTrue();
        return Long.parseLong(matcher.group(1));
    }

    private int count(String sql, Object arg) {
        return jdbcTemplate.queryForObject(sql, Integer.class, arg);
    }

    private long userId(String email) {
        return jdbcTemplate.queryForObject("SELECT user_id FROM app_user WHERE email = ?", Long.class, email);
    }

    private void clearRateLimit(long userId) {
        redisTemplate.delete(List.of(
                "siot:rate:" + BillTranslationService.RATE_LIMIT_KEY + userId + ":interval",
                "siot:rate:" + BillTranslationService.RATE_LIMIT_KEY + userId + ":count"));
    }

    private static MockMultipartFile jpeg() {
        return new MockMultipartFile("file", "bill.jpg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10});
    }

    private Cookie login(String email) throws Exception {
        Integer exists = jdbcTemplate.queryForObject("SELECT count(*) FROM app_user WHERE email = ?", Integer.class, email);
        if (exists == 0) {
            redisTemplate.opsForValue().set(SignupVerificationService.VERIFIED_KEY + email, "1");
            mockMvc.perform(post("/api/auth/signup").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                                    + "\",\"passwordConfirm\":\"" + PASSWORD + "\"}"))
                    .andExpect(status().isCreated());
        }
        MvcResult result = mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie("SIOT_SESSION");
    }
}
