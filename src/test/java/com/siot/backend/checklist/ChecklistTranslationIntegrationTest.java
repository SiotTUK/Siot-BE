package com.siot.backend.checklist;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * COM-02 체크리스트 본문 언어 계약 (BE #30): 한국어 요청 / 요청 언어 번역 / 번역 누락 후 한국어 대체를
 * {@code contentLanguage}·{@code translated} 로 구분한다. 경고 문구·분류 이름은 본문 언어와 별개로 요청 언어.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ChecklistTranslationIntegrationTest {

    /** 시드에 한국어 문구·조건부 문구만 있고, 항상 보이는 항목 */
    private static final String CODE = "CL-04";
    private static final String ITEM = "$.data.items[?(@.code == '" + CODE + "')]";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    StringRedisTemplate redisTemplate;

    @Test
    void checklistTellsWhetherTheBodyIsInTheRequestedLanguage() throws Exception {
        Cookie user = signup();

        // 한국어 요청: 원문 그대로
        checklist(user, "ko")
                .andExpect(jsonPath(ITEM + ".contentLanguage").value("ko"))
                .andExpect(jsonPath(ITEM + ".translated").value(true))
                .andExpect(jsonPath(ITEM + ".categoryLabel").value("건강보험"));

        // 영어 번역이 없으면 한국어 원문으로 대체 → '번역 준비 중'. 분류 이름은 요청 언어
        checklist(user, "en")
                .andExpect(jsonPath(ITEM + ".contentLanguage").value("ko"))
                .andExpect(jsonPath(ITEM + ".translated").value(false))
                .andExpect(jsonPath(ITEM + ".title").value("건강보험 가입 확인"))
                .andExpect(jsonPath(ITEM + ".categoryLabel").value("Health insurance"));

        Long guideId = jdbcTemplate.queryForObject("SELECT guide_id FROM guide WHERE code = ?", Long.class, CODE);
        try {
            // 본문만 번역되고 조건부 문구는 한국어로 대체되면 아직 '번역 준비 중'
            jdbcTemplate.update("INSERT INTO guide_translation (language, guide_id, title, content) VALUES ('en', ?, ?, ?)",
                    guideId, "Check your health insurance", "Body in English");
            checklist(user, "en")
                    .andExpect(jsonPath(ITEM + ".contentLanguage").value("en"))
                    .andExpect(jsonPath(ITEM + ".title").value("Check your health insurance"))
                    .andExpect(jsonPath(ITEM + ".translated").value(false));

            // 조건부 문구까지 번역되면 정상 번역
            jdbcTemplate.update("""
                    INSERT INTO guide_variant_translation (guide_id, language, variant_key, body)
                    SELECT guide_id, 'en', variant_key, 'EN ' || variant_key FROM guide_variant_translation
                    WHERE guide_id = ? AND language = 'ko'""", guideId);
            checklist(user, "en")
                    .andExpect(jsonPath(ITEM + ".contentLanguage").value("en"))
                    .andExpect(jsonPath(ITEM + ".translated").value(true));
        } finally {
            jdbcTemplate.update("DELETE FROM guide_variant_translation WHERE guide_id = ? AND language = 'en'", guideId);
            jdbcTemplate.update("DELETE FROM guide_translation WHERE guide_id = ? AND language = 'en'", guideId);
        }
    }

    private ResultActions checklist(Cookie user, String language) throws Exception {
        return mockMvc.perform(get("/api/members/me/checklist").param("language", language).cookie(user))
                .andExpect(status().isOk());
    }

    private Cookie signup() throws Exception {
        String email = "t" + ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L) + "@example.com";
        redisTemplate.opsForValue().set(SignupVerificationService.VERIFIED_KEY + email, "1");
        return mockMvc.perform(post("/api/auth/signup").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"abcd1234","passwordConfirm":"abcd1234"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getCookie("SIOT_SESSION");
    }
}
