package com.siot.backend.auth;

import com.siot.backend.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M-01 ~ M-03, M-08 ~ M-10 회원가입·로그인·로그아웃 통합 테스트 (PostgreSQL + Redis 세션).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthIntegrationTest {

    private static final String SESSION_COOKIE = "SIOT_SESSION";
    private static final String PASSWORD = "abcd1234";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    // ---------------------------------------------------------------- M-01, M-02, M-03

    @Test
    void signupCreatesMemberAndLogsIn() throws Exception {
        String loginId = newLoginId();

        MvcResult result = signup(loginId, PASSWORD, PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.loginId").value(loginId))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(cookie().exists(SESSION_COOKIE))
                .andExpect(cookie().httpOnly(SESSION_COOKIE, true))
                .andExpect(cookie().sameSite(SESSION_COOKIE, "Lax"))
                .andReturn();

        mockMvc.perform(get("/api/members/me").cookie(sessionCookie(result)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginId").value(loginId));

        // NFR-02: 비밀번호는 단방향 해시로만 저장
        String hash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM app_user WHERE login_id = ?", String.class, loginId);
        assertThat(hash).startsWith("{bcrypt}").doesNotContain(PASSWORD);
    }

    @Test
    void signupRejectsDuplicateLoginId() throws Exception {
        String loginId = newLoginId();
        signup(loginId, PASSWORD, PASSWORD).andExpect(status().isCreated());

        signup(loginId, PASSWORD, PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_LOGIN_ID"));
    }

    @Test
    void loginIdAvailability() throws Exception {
        String loginId = newLoginId();
        checkAvailability(loginId).andExpect(jsonPath("$.data.available").value(true));

        signup(loginId, PASSWORD, PASSWORD).andExpect(status().isCreated());

        checkAvailability(loginId).andExpect(jsonPath("$.data.available").value(false));
        checkAvailability("Bad-ID")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LOGIN_ID_FORMAT"));
    }

    @Test
    void signupValidatesLoginIdAndPasswordRules() throws Exception {
        String loginId = newLoginId();
        expectSignupError("ab1", PASSWORD, PASSWORD, "LOGIN_ID_FORMAT");            // 4자 미만
        expectSignupError("Abcd1234", PASSWORD, PASSWORD, "LOGIN_ID_FORMAT");       // 대문자
        expectSignupError(loginId, "abc123", "abc123", "PASSWORD_LENGTH");
        expectSignupError(loginId, "abcdefgh", "abcdefgh", "PASSWORD_LETTER_AND_DIGIT_REQUIRED");
        expectSignupError(loginId, "abcd 1234", "abcd 1234", "PASSWORD_WHITESPACE");
        expectSignupError(loginId, "abcd1234가", "abcd1234가", "PASSWORD_INVALID_CHARACTER");
        expectSignupError("user1234", "user1234", "user1234", "PASSWORD_SAME_AS_LOGIN_ID");
        expectSignupError(loginId, PASSWORD, "abcd12345", "PASSWORD_CONFIRM_MISMATCH");
    }

    // ---------------------------------------------------------------- M-08, M-09, M-10

    @Test
    void loginAndLogout() throws Exception {
        String loginId = newLoginId();
        signup(loginId, PASSWORD, PASSWORD).andExpect(status().isCreated());

        login(loginId, "wrong1234")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));

        MvcResult result = login(loginId, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginId").value(loginId))
                .andReturn();
        Cookie session = sessionCookie(result);

        mockMvc.perform(get("/api/members/me").cookie(session))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/logout").cookie(session).with(csrf()))
                .andExpect(status().isNoContent());

        // 로그아웃한 세션으로는 더 이상 로그인 상태가 아니다
        mockMvc.perform(get("/api/members/me").cookie(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void unknownLoginIdGetsSameErrorAsWrongPassword() throws Exception {
        login(newLoginId(), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
    }

    @Test
    void fiveConsecutiveFailuresLockLoginForFiveMinutes() throws Exception {
        String loginId = newLoginId();
        signup(loginId, PASSWORD, PASSWORD).andExpect(status().isCreated());

        for (int i = 0; i < 5; i++) {
            login(loginId, "wrong1234").andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
        }

        // 잠금 중에는 올바른 비밀번호도 거부
        login(loginId, PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_LOCKED"));
    }

    @Test
    void successfulLoginResetsFailureCount() throws Exception {
        String loginId = newLoginId();
        signup(loginId, PASSWORD, PASSWORD).andExpect(status().isCreated());

        for (int i = 0; i < 4; i++) {
            login(loginId, "wrong1234").andExpect(status().isUnauthorized());
        }
        login(loginId, PASSWORD).andExpect(status().isOk());
        for (int i = 0; i < 4; i++) {
            login(loginId, "wrong1234").andExpect(status().isUnauthorized());
        }
        login(loginId, PASSWORD).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- helpers

    private ResultActions signup(String loginId, String password, String passwordConfirm) throws Exception {
        return mockMvc.perform(post("/api/auth/signup").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"loginId":"%s","password":"%s","passwordConfirm":"%s"}
                        """.formatted(loginId, password, passwordConfirm)));
    }

    private void expectSignupError(String loginId, String password, String confirm, String code) throws Exception {
        signup(loginId, password, confirm)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(code));
    }

    private ResultActions login(String loginId, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"loginId":"%s","password":"%s"}
                        """.formatted(loginId, password)));
    }

    private ResultActions checkAvailability(String loginId) throws Exception {
        return mockMvc.perform(get("/api/auth/login-id/availability").param("loginId", loginId));
    }

    private Cookie sessionCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(cookie)
                .as("session cookie, Set-Cookie headers=%s", result.getResponse().getHeaders("Set-Cookie"))
                .isNotNull();
        return cookie;
    }

    private String newLoginId() {
        return "t" + ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L);
    }
}
