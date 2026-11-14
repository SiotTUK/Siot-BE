package com.siot.backend.member;

import com.siot.backend.TestcontainersConfiguration;
import com.siot.backend.auth.service.SignupVerificationService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M-01 가입 인증 코드, NTF-09 가입 후 이메일 인증, M-11 비밀번호 재설정 통합 테스트.
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

    // ================================================================ M-01 가입 인증 코드

    @Test
    void signupRequiresVerifiedEmailCode() throws Exception {
        String email = newEmail();

        // 코드를 확인하지 않으면 가입할 수 없다
        signupRequest(email).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

        mockMvc.perform(post("/api/auth/signup/email-verification").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"language\":\"en\"}".formatted(email.toUpperCase())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(600));

        EmailMessage mail = lastMail();
        assertThat(mail.to()).isEqualTo(email);
        assertThat(mail.subject()).isEqualTo(mailTemplates.emailVerification(email, "en", "1", 1).subject());
        String code = extractCode(mail.body());

        confirmSignupCode(email, wrongCode(code))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_CODE_INVALID"));
        confirmSignupCode(email, code).andExpect(status().isNoContent());

        signupRequest(email).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.emailVerified").value(true));
    }

    @Test
    void signupCodeIsNotSentForRegisteredOrInvalidEmail() throws Exception {
        String email = newEmail();
        signup(email);

        requestSignupCode(email).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
        requestSignupCode("not-an-email").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_FORMAT"));
        verify(emailSender, never()).send(any());
    }

    @Test
    void signupCodeLimitsAndExpiry() throws Exception {
        String email = newEmail();
        requestSignupCode(email).andExpect(status().isAccepted());
        String code = extractCode(lastMail().body());

        requestSignupCode(email).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_TOO_FREQUENT"));

        for (int i = 0; i < 4; i++) {
            confirmSignupCode(email, wrongCode(code)).andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_CODE_INVALID"));
        }
        confirmSignupCode(email, wrongCode(code)).andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_EXPIRED"));
        confirmSignupCode(email, code).andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_EXPIRED"));
        // 다른 이메일의 코드로는 확인되지 않는다
        confirmSignupCode(newEmail(), code).andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_EXPIRED"));
    }

    @Test
    void verifiedSignupEmailCanBeUsedOnlyOnce() throws Exception {
        String email = newEmail();
        signup(email);
        jdbcTemplate.update("DELETE FROM app_user WHERE email = ?", email);

        signupRequest(email).andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }

    // ================================================================ NTF-09 가입 후 인증 (기존 미인증 회원)

    @Test
    void verifySignupEmailWithCode() throws Exception {
        String email = newEmail();
        Cookie session = signup(email);
        markUnverified(email);

        mockMvc.perform(post("/api/members/me/email/verification").cookie(session).with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(600));

        EmailMessage mail = lastMail();
        assertThat(mail.to()).isEqualTo(email);
        String code = extractCode(mail.body());

        confirmCode(session, wrongCode(code))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_CODE_INVALID"));

        confirmCode(session, code)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.emailVerified").value(true));

        // 이미 인증했으면 코드를 다시 보내지 않는다
        requestCode(session)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_VERIFIED"));
    }

    @Test
    void verificationCodeCanBeRequestedOncePerMinute() throws Exception {
        String email = newEmail();
        Cookie session = signup(email);
        markUnverified(email);
        requestCode(session).andExpect(status().isAccepted());

        requestCode(session)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_TOO_FREQUENT"));
    }

    @Test
    void fiveWrongCodesInvalidateVerification() throws Exception {
        String email = newEmail();
        Cookie session = signup(email);
        markUnverified(email);
        requestCode(session).andExpect(status().isAccepted());
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
        mockMvc.perform(post("/api/members/me/email/verification").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    // ================================================================ M-11

    @Test
    void resetPasswordWithEmailLink() throws Exception {
        String loginId = newEmail();
        Cookie oldSession = signup(loginId);
        markVerified(loginId);

        requestReset(loginId.toUpperCase()).andExpect(status().isAccepted());

        EmailMessage mail = lastMail();
        assertThat(mail.to()).isEqualTo(loginId);
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
        String unverified = newEmail();
        signup(unverified);
        markUnverified(unverified);

        requestReset(unverified).andExpect(status().isAccepted());   // 이메일 미인증
        requestReset(newEmail()).andExpect(status().isAccepted());   // 가입하지 않은 이메일

        verify(emailSender, never()).send(any());
    }

    @Test
    void resetRequestLimitedToOncePerMinute() throws Exception {
        String loginId = newEmail();
        requestReset(loginId).andExpect(status().isAccepted());

        requestReset(loginId)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_TOO_FREQUENT"));
    }

    @Test
    void newResetLinkInvalidatesPreviousOne() throws Exception {
        String loginId = newEmail();
        signup(loginId);
        markVerified(loginId);

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
        String loginId = newEmail();
        signup(loginId);
        markVerified(loginId);
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
            EmailMessage reset = mailTemplates.passwordReset("x@example.com", language, "http://link", 30);

            assertThat(verification.body()).contains("123456", "10").doesNotContain("{");
            assertThat(reset.body()).contains("x@example.com", "http://link", "30").doesNotContain("{");
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

    private void markUnverified(String email) {
        jdbcTemplate.update("UPDATE app_user SET email_verified_at = NULL WHERE email = ?", email);
    }

    private void markVerified(String email) {
        jdbcTemplate.update("UPDATE app_user SET email_verified_at = now() WHERE email = ?", email);
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

    /** 가입 인증 코드 확인을 마친 상태로 만든 뒤 가입한다 (메일 mock 기록은 지운다) */
    private Cookie signup(String email) throws Exception {
        redisTemplate.opsForValue().set(SignupVerificationService.VERIFIED_KEY + email, "1");
        MvcResult result = signupRequest(email).andExpect(status().isCreated()).andReturn();
        clearInvocations(emailSender);
        return result.getResponse().getCookie("SIOT_SESSION");
    }

    private ResultActions signupRequest(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/signup").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s","passwordConfirm":"%s"}
                        """.formatted(email, PASSWORD, PASSWORD)));
    }

    private ResultActions requestSignupCode(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/signup/email-verification").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\"}".formatted(email)));
    }

    private ResultActions confirmSignupCode(String email, String code) throws Exception {
        return mockMvc.perform(post("/api/auth/signup/email-verification/confirm").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"code\":\"%s\"}".formatted(email, code)));
    }

    private ResultActions login(String loginId, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(loginId, password)));
    }

    private ResultActions requestCode(Cookie session) throws Exception {
        return mockMvc.perform(post("/api/members/me/email/verification").cookie(session).with(csrf()));
    }

    private ResultActions confirmCode(Cookie session, String code) throws Exception {
        return mockMvc.perform(post("/api/members/me/email/verification/confirm").cookie(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"%s\"}".formatted(code)));
    }

    private ResultActions requestReset(String loginId) throws Exception {
        return mockMvc.perform(post("/api/auth/password-reset").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\"}".formatted(loginId)));
    }

    private ResultActions confirmReset(String token, String password, String confirm) throws Exception {
        return mockMvc.perform(post("/api/auth/password-reset/confirm").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"token":"%s","password":"%s","passwordConfirm":"%s"}
                        """.formatted(token, password, confirm)));
    }

    private String newEmail() {
        return "e" + ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L) + "@example.com";
    }
}
