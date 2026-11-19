package com.siot.backend.member;

import com.siot.backend.TestcontainersConfiguration;
import com.siot.backend.auth.service.SignupVerificationService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 온보딩 답 저장·조회 (F02~F06), 화면 언어 수정 통합 테스트.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MemberProfileIntegrationTest {

    private static final String PASSWORD = "abcd1234";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Test
    void onboardingAnswersAreSavedAndReturnedByMe() throws Exception {
        Cookie session = signup();

        mockMvc.perform(get("/api/members/me").cookie(session))
                .andExpect(jsonPath("$.data.onboarded").value(false))
                .andExpect(jsonPath("$.data.userType").doesNotExist())
                .andExpect(jsonPath("$.data.residences").isEmpty());

        saveOnboarding(session, """
                {"userType":"NEW","entryDate":"2026-11-01","purpose":"STUDY",
                 "residences":["KOREAN","KIDS"],"stayExpiry":"2027-11-01"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboarded").value(true));

        mockMvc.perform(get("/api/members/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userType").value("NEW"))
                .andExpect(jsonPath("$.data.entryDate").value("2026-11-01"))
                .andExpect(jsonPath("$.data.purpose").value("STUDY"))
                // enum 선언 순서
                .andExpect(jsonPath("$.data.residences[0]").value("KIDS"))
                .andExpect(jsonPath("$.data.residences[1]").value("KOREAN"))
                .andExpect(jsonPath("$.data.stayExpiry").value("2027-11-01"));
    }

    @Test
    void onboardingOverwritesAllAnswers() throws Exception {
        Cookie session = signup();
        saveOnboarding(session, """
                {"userType":"NEW","entryDate":"2026-11-01","purpose":"WORK","residences":["CAR"],"stayExpiry":"2027-11-01"}
                """).andExpect(status().isOk());

        // 내 정보 화면에서 고칠 때: 선택 항목을 비우면 지워진다
        saveOnboarding(session, """
                {"userType":"LIVING"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userType").value("LIVING"))
                .andExpect(jsonPath("$.data.entryDate").doesNotExist())
                .andExpect(jsonPath("$.data.purpose").doesNotExist())
                .andExpect(jsonPath("$.data.residences").isEmpty())
                .andExpect(jsonPath("$.data.stayExpiry").doesNotExist());
    }

    @Test
    void onboardingRequiresUserTypeAndValidValues() throws Exception {
        Cookie session = signup();

        saveOnboarding(session, """
                {"purpose":"STUDY"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        saveOnboarding(session, """
                {"userType":"NEW","residences":["BOAT"]}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void onboardingRequiresLogin() throws Exception {
        mockMvc.perform(put("/api/members/me/onboarding").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userType\":\"NEW\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void languageCanBeChanged() throws Exception {
        Cookie session = signup();

        updateMe(session, "{\"language\":\"zh\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.language").value("zh"));
        mockMvc.perform(get("/api/members/me").cookie(session))
                .andExpect(jsonPath("$.data.language").value("zh"));

        // 보내지 않으면 그대로
        updateMe(session, "{}").andExpect(jsonPath("$.data.language").value("zh"));

        updateMe(session, "{\"language\":\"fr\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    // ---------------------------------------------------------------- helpers

    private Cookie signup() throws Exception {
        String loginId = "t" + ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L) + "@example.com";
        redisTemplate.opsForValue().set(SignupVerificationService.VERIFIED_KEY + loginId, "1");
        MvcResult result = mockMvc.perform(post("/api/auth/signup").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","passwordConfirm":"%s"}
                                """.formatted(loginId, PASSWORD, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();
        return result.getResponse().getCookie("SIOT_SESSION");
    }

    private ResultActions saveOnboarding(Cookie session, String body) throws Exception {
        return mockMvc.perform(put("/api/members/me/onboarding").cookie(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions updateMe(Cookie session, String body) throws Exception {
        return mockMvc.perform(patch("/api/members/me").cookie(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
