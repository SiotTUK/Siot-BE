package com.siot.backend.member;

import com.siot.backend.TestcontainersConfiguration;
import com.siot.backend.auth.service.SignupVerificationService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M-06 / M-07 회원 탈퇴 통합 테스트.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class WithdrawalIntegrationTest {

    private static final String PASSWORD = "abcd1234";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    FindByIndexNameSessionRepository<?> sessionRepository;

    @Test
    void withdrawalRequiresCorrectPassword() throws Exception {
        String loginId = newLoginId();
        Cookie session = sessionCookie(signup(loginId));

        withdraw(session, "wrong1234")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_INCORRECT"));

        // 회원과 로그인 상태가 그대로 유지된다
        assertThat(countUsers(loginId)).isEqualTo(1);
        mockMvc.perform(get("/api/members/me").cookie(session)).andExpect(status().isOk());
    }

    @Test
    void withdrawalRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/members/me/withdrawal").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"abcd1234\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void withdrawalDeletesMemberDataKeepsReportsAndEndsAllSessions() throws Exception {
        String loginId = newLoginId();
        Cookie firstDevice = sessionCookie(signup(loginId));
        Cookie secondDevice = sessionCookie(login(loginId));
        Long userId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM app_user WHERE email = ?", Long.class, loginId);
        Long reportId = createMemberData(userId);

        assertThat(sessionRepository.findByPrincipalName(loginId)).hasSize(2);

        withdraw(firstDevice, PASSWORD).andExpect(status().isNoContent());

        // M-07: 회원 정보와 소유 데이터는 물리 삭제
        assertThat(countUsers(loginId)).isZero();
        assertThat(count("checklist_progress", userId)).isZero();
        assertThat(count("member_residence", userId)).isZero();
        assertThat(count("member_life_change", userId)).isZero();
        assertThat(count("notification", userId)).isZero();
        assertThat(count("notification_preference", userId)).isZero();
        assertThat(count("chat_conversation", userId)).isZero();

        // 오류 신고는 남기고 작성자만 지운다
        assertThat(jdbcTemplate.queryForObject(
                "SELECT reporter_user_id FROM content_report WHERE report_id = ?", Long.class, reportId)).isNull();

        // 모든 기기의 세션이 끊긴다
        assertThat(sessionRepository.findByPrincipalName(loginId)).isEmpty();
        mockMvc.perform(get("/api/members/me").cookie(firstDevice)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/members/me").cookie(secondDevice)).andExpect(status().isUnauthorized());

        // 복구할 수 없고, 같은 이메일로 다시 가입할 수 있다
        login(loginId).andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
        mockMvc.perform(get("/api/auth/email/availability").param("email", loginId))
                .andExpect(jsonPath("$.data.available").value(true));
    }

    /** 탈퇴 시 정리되어야 할 회원 소유 데이터를 만든다. 생성한 오류 신고 ID를 돌려준다. */
    private Long createMemberData(Long userId) {
        Long guideId = jdbcTemplate.queryForObject(
                "SELECT guide_id FROM guide WHERE code = 'CL-04'", Long.class);
        jdbcTemplate.update(
                "INSERT INTO checklist_progress (user_id, guide_id, status) VALUES (?, ?, 'DONE')", userId, guideId);
        jdbcTemplate.update("INSERT INTO member_residence (user_id, residence) VALUES (?, 'CAR')", userId);
        jdbcTemplate.update(
                "INSERT INTO member_life_change (user_id, life_change_code, base_date) VALUES (?, 'LC-01', current_date)",
                userId);
        jdbcTemplate.update("""
                INSERT INTO notification (user_id, kind, scheduled_at, title, body)
                VALUES (?, 'TAX', now(), 't', 'b')""", userId);
        jdbcTemplate.update(
                "INSERT INTO notification_preference (kind, user_id) VALUES ('TAX', ?)", userId);
        Long conversationId = jdbcTemplate.queryForObject(
                "INSERT INTO chat_conversation (user_id) VALUES (?) RETURNING conversation_id", Long.class, userId);
        jdbcTemplate.update(
                "INSERT INTO chat_message (conversation_id, role, content) VALUES (?, 'USER', 'q')", conversationId);
        return jdbcTemplate.queryForObject("""
                INSERT INTO content_report (reporter_user_id, guide_id, reason)
                VALUES (?, ?, 'TRANSLATION') RETURNING report_id""", Long.class, userId, guideId);
    }

    private int count(String table, Long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE user_id = ?", Integer.class, userId);
    }

    private int countUsers(String loginId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM app_user WHERE email = ?", Integer.class, loginId);
    }

    private MvcResult signup(String loginId) throws Exception {
        // 가입 인증 코드 확인을 마친 상태로 만든다 (코드 발송 흐름은 EmailAndPasswordResetIntegrationTest)
        redisTemplate.opsForValue().set(
                SignupVerificationService.VERIFIED_KEY + loginId.trim().toLowerCase(java.util.Locale.ROOT), "1");
        return mockMvc.perform(post("/api/auth/signup").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","passwordConfirm":"%s"}
                                """.formatted(loginId, PASSWORD, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private ResultActions login(String loginId) throws Exception {
        return mockMvc.perform(post("/api/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(loginId, PASSWORD)));
    }

    private Cookie sessionCookie(ResultActions actions) throws Exception {
        return sessionCookie(actions.andExpect(status().isOk()).andReturn());
    }

    private Cookie sessionCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("SIOT_SESSION");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private ResultActions withdraw(Cookie session, String password) throws Exception {
        return mockMvc.perform(post("/api/members/me/withdrawal").cookie(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"%s\"}".formatted(password)));
    }

    private String newLoginId() {
        return "w" + ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L) + "@example.com";
    }
}
