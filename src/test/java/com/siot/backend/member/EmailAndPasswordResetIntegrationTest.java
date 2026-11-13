package com.siot.backend.member;

import com.siot.backend.TestcontainersConfiguration;
import com.siot.backend.global.mail.EmailMessage;
import com.siot.backend.global.mail.EmailSender;
import com.siot.backend.global.mail.MailTemplates;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * NTF-09 알림 이메일 등록·인증, M-11 비밀번호 재설정 통합 테스트.
 * 실제 메일 대신 EmailSender 를 mock 으로 바꿔 보낸 내용을 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EmailAndPasswordResetIntegrationTest {

    private static final String PASSWORD = "abcd1234";
    private static final String NEW_PASSWORD = "newpass5678";
    private static final Pattern CODE = Pattern.compile("(\\d{6})");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    MailTemplates mailTemplates;

    @MockitoBean
    EmailSender emailSender;

    @BeforeEach
    void resetMock() {
        clearInvocations(emailSender);
    }

    // ================================================================ NTF-09

    @Test
    void registerEmailWithVerificationCode() throws Exception {
        Cookie session = signup(newLoginId());

        mockMvc.perform(post("/api/members/me/email/verification").cookie(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"User@Example.com\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.email").value("user@example.com"))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(600));

        EmailMessage mail = lastMail();
        assertThat(mail.to()).isEqualTo("user@example.com");
        String code = extractCode(mail.body());

        confirmCode(session, wrongCode(code))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_CODE_INVALID"));

        confirmCode(session, code)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.emailRegistered").value(true))
                .andExpect(jsonPath("$.data.emailVerified").value(true));

        // 등록 해제
        mockMvc.perform(delete("/api/members/me/email").cookie(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.emailRegistered").value(false));
    }

    @Test
    void verificationCodeCanBeRequestedOncePerMinute() throws Exception {
        Cookie session = signup(newLoginId());
        requestCode(session, "a@example.com").andExpect(status().isAccepted());

        requestCode(session, "a@example.com")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_TOO_FREQUENT"));
    }

    @Test
    void fiveWrongCodesInvalidateVerification() throws Exception {
        Cookie session = signup(newLoginId());
        requestCode(session, "b@example.com").andExpect(status().isAccepted());
        String code = extractCode(lastMail().body());

        for (int i = 0; i < 4; i++) {
            confirmCode(session, wrongCode(code)).andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_CODE_INVALID"));
        }
        confirmCode(session, wrongCode(code)).andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_EXPIRED"));
        // 올바른 코드도 더 이상 쓸 수 없다
        confirmCode(session, code).andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_EXPIRED"));
    }

    @Test
    void emailRegistrationRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/members/me/email/verification").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"c@example.com\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ================================================================ M-11

    @Test
    void resetPasswordWithEmailLink() throws Exception {
        String loginId = newLoginId();
        Cookie oldSession = signup(loginId);
        setVerifiedEmail(loginId, "reset@example.com");

        requestReset(loginId).andExpect(status().isAccepted());

        EmailMessage mail = lastMail();
        assertThat(mail.to()).isEqualTo("reset@example.com");
        assertThat(mail.body()).contains(loginId);
        String token = extractToken(mail.body());

        mockMvc.perform(get("/api/auth/password-reset/validation").param("token", token))
                .andExpect(status().isNoContent());

        // 규칙 위반으로 실패해도 링크는 소모되지 않는다
        confirmReset(token, "short1", "short1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_LENGTH"));

        confirmReset(token, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        // 1회용
        confirmReset(token, NEW_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_TOKEN_INVALID"));

        // 기존 세션은 모두 끊기고, 새 비밀번호로만 로그인된다
        mockMvc.perform(get("/api/members/me").cookie(oldSession)).andExpect(status().isUnauthorized());
        login(loginId, PASSWORD).andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
        login(loginId, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void sameResponseWhetherOrNotAccountOrEmailExists() throws Exception {
        String withoutEmail = newLoginId();
        signup(withoutEmail);

        requestReset(withoutEmail).andExpect(status().isAccepted());   // 이메일 미등록
        requestReset(newLoginId()).andExpect(status().isAccepted());   // 없는 ID

        verify(emailSender, never()).send(any());
    }

    @Test
    void resetRequestLimitedToOncePerMinute() throws Exception {
        String loginId = newLoginId();
        requestReset(loginId).andExpect(status().isAccepted());

        requestReset(loginId)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_TOO_FREQUENT"));
    }

    @Test
    void newResetLinkInvalidatesPreviousOne() throws Exception {
        String loginId = newLoginId();
        signup(loginId);
        setVerifiedEmail(loginId, "twice@example.com");

        requestReset(loginId).andExpect(status().isAccepted());
        String firstToken = extractToken(lastMail().body());
        // 1분 제한을 지난 것처럼 만든다
        clearRateLimit(loginId);
        requestReset(loginId).andExpect(status().isAccepted());

        mockMvc.perform(get("/api/auth/password-reset/validation").param("token", firstToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void passwordResetUnlocksLockedLogin() throws Exception {
        String loginId = newLoginId();
        signup(loginId);
        setVerifiedEmail(loginId, "locked@example.com");
        for (int i = 0; i < 5; i++) {
            login(loginId, "wrong1234");
        }
        login(loginId, PASSWORD).andExpect(jsonPath("$.code").value("LOGIN_LOCKED"));

        requestReset(loginId).andExpect(status().isAccepted());
        confirmReset(extractToken(lastMail().body()), NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        login(loginId, NEW_PASSWORD).andExpect(status().isOk());
    }

    // ================================================================ 메일 문구

    @Test
    void mailTemplatesExistForAllLanguages() {
        for (String language : List.of("ko", "en", "zh", "vi")) {
            EmailMessage verification = mailTemplates.emailVerification("x@example.com", language, "123456", 10);
            EmailMessage reset = mailTemplates.passwordReset("x@example.com", language, "user01", "http://link", 30);

            assertThat(verification.body()).contains("123456", "10").doesNotContain("{");
            assertThat(reset.body()).contains("user01", "http://link", "30").doesNotContain("{");
        }
        assertThat(mailTemplates.emailVerification("x@example.com", "en", "1", 1).subject())
                .isNotEqualTo(mailTemplates.emailVerification("x@example.com", "ko", "1", 1).subject());
    }

    // ================================================================ helpers

    @Autowired
    org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    private void clearRateLimit(String loginId) {
        redisTemplate.delete("siot:rate:password-reset:" + loginId + ":interval");
    }

    private void setVerifiedEmail(String loginId, String email) {
        jdbcTemplate.update("UPDATE app_user SET email = ?, email_verified_at = now() WHERE login_id = ?",
                email, loginId);
    }

    private EmailMessage lastMail() {
        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender, org.mockito.Mockito.atLeastOnce()).send(captor.capture());
        return captor.getValue();
    }

    private String extractCode(String body) {
        Matcher matcher = CODE.matcher(body);
        assertThat(matcher.find()).as("code in mail body").isTrue();
        return matcher.group(1);
    }

    private String wrongCode(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }

    private String extractToken(String body) {
        String url = body.lines().filter(line -> line.startsWith("http")).findFirst().orElseThrow();
        assertThat(url).startsWith("http://localhost:5173/password-reset?token=");
        return UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst("token");
    }

    private Cookie signup(String loginId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/signup").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"%s","password":"%s","passwordConfirm":"%s"}
                                """.formatted(loginId, PASSWORD, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();
        return result.getResponse().getCookie("SIOT_SESSION");
    }

    private ResultActions login(String loginId, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"%s\",\"password\":\"%s\"}".formatted(loginId, password)));
    }

    private ResultActions requestCode(Cookie session, String email) throws Exception {
        return mockMvc.perform(post("/api/members/me/email/verification").cookie(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\"}".formatted(email)));
    }

    private ResultActions confirmCode(Cookie session, String code) throws Exception {
        return mockMvc.perform(post("/api/members/me/email/verification/confirm").cookie(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"%s\"}".formatted(code)));
    }

    private ResultActions requestReset(String loginId) throws Exception {
        return mockMvc.perform(post("/api/auth/password-reset").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"%s\"}".formatted(loginId)));
    }

    private ResultActions confirmReset(String token, String password, String confirm) throws Exception {
        return mockMvc.perform(post("/api/auth/password-reset/confirm").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"token":"%s","password":"%s","passwordConfirm":"%s"}
                        """.formatted(token, password, confirm)));
    }

    private String newLoginId() {
        return "e" + ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L);
    }
}
