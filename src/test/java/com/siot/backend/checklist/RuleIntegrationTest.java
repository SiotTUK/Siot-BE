package com.siot.backend.checklist;

import com.siot.backend.TestcontainersConfiguration;
import com.siot.backend.auth.service.SignupVerificationService;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 생활수칙 CRUD (RUL-09~13, ADM-06) API 통합 테스트: 관리자 등록·목록·수정·삭제, 사용자 조회, 입력 검사, 코드 중복.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RuleIntegrationTest {

    private static final String PASSWORD = "abcd1234";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    StringRedisTemplate redisTemplate;

    /** 실제 Gemini 대신 "EN 원문" 형식으로 번역한다 */
    @MockitoBean
    Translator translator;

    private Cookie admin;
    private Cookie user;
    private String code;
    private String category;

    @BeforeEach
    void setUp() throws Exception {
        given(translator.translateAll(anyString())).willAnswer(inv -> Map.of(
                "en", "EN " + inv.getArgument(0), "zh", "ZH " + inv.getArgument(0), "vi", "VI " + inv.getArgument(0)));
        int n = ThreadLocalRandom.current().nextInt(100_000, 999_999);
        code = "RUL-T" + n;
        category = "테스트 분류 " + n;

        String adminEmail = newEmail();
        signup(adminEmail);
        jdbcTemplate.update("UPDATE app_user SET role = 'ADMIN' WHERE email = ?", adminEmail);
        admin = login(adminEmail); // 권한은 로그인할 때 적용된다
        user = signup(newEmail());
    }

    @Test
    void adminCreatesUpdatesListsAndDeletesRule() throws Exception {
        // 등록 (RUL-09): 번역본과 카테고리명 번역을 함께 만든다 (RUL-13)
        create(code, category, "분리배출 방법", "본문입니다", true)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value(code))
                .andExpect(jsonPath("$.data.category").value(category))
                .andExpect(jsonPath("$.data.title").value("분리배출 방법"));

        // 사용자 조회: 목록·상세·FAQ·카테고리, 요청 언어로
        mockMvc.perform(get("/api/rules").param("category", category).cookie(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value(code));
        mockMvc.perform(get("/api/rules/{code}", code).param("language", "en").cookie(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("EN 분리배출 방법"))
                .andExpect(jsonPath("$.data.content").value("EN 본문입니다"))
                .andExpect(jsonPath("$.data.categoryName").value("EN " + category));
        mockMvc.perform(get("/api/rules/faq").cookie(user))
                .andExpect(jsonPath("$.data[*].code", hasItem(code)));
        mockMvc.perform(get("/api/rules/categories").param("language", "zh").cookie(user))
                .andExpect(jsonPath("$.data[?(@.category == '" + category + "')].name", hasItem("ZH " + category)));

        // 관리자 목록 (ADM-06)
        mockMvc.perform(get("/api/admin/rules").param("keyword", code).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("분리배출 방법"))
                .andExpect(jsonPath("$.data[0].faq").value(true))
                .andExpect(jsonPath("$.data[0].deleted").value(false))
                .andExpect(jsonPath("$.data[0].missingLanguages.length()").value(0));

        // 수정 (RUL-10): 원문이 바뀌면 번역을 다시 만든다 (COM-02)
        update(code, null, "바뀐 제목", "바뀐 본문")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("바뀐 제목"))
                .andExpect(jsonPath("$.data.category").value(category)); // 보내지 않으면 그대로
        mockMvc.perform(get("/api/rules/{code}", code).param("language", "vi").cookie(user))
                .andExpect(jsonPath("$.data.title").value("VI 바뀐 제목"));

        // 삭제 (RUL-11): 사용자 화면에서 사라지고, 관리자는 includeDeleted 로 본다
        mockMvc.perform(delete("/api/admin/rules/{code}", code).cookie(admin).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/rules/{code}", code).cookie(user))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GUIDE_NOT_FOUND"));
        mockMvc.perform(get("/api/rules/faq").cookie(user))
                .andExpect(jsonPath("$.data[*].code", not(hasItem(code))));
        mockMvc.perform(get("/api/admin/rules").param("keyword", code).cookie(admin))
                .andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(get("/api/admin/rules").param("keyword", code).param("includeDeleted", "true").cookie(admin))
                .andExpect(jsonPath("$.data[0].deleted").value(true))
                .andExpect(jsonPath("$.data[0].deletedAt").isNotEmpty());
        // 행은 남는다 (이미 보낸 알림·신고가 참조)
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM guide WHERE code = ?", Integer.class, code))
                .isEqualTo(1);

        // 삭제한 항목은 수정·삭제할 수 없다
        update(code, null, "제목", "본문").andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/admin/rules/{code}", code).cookie(admin).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void duplicateOrDeletedCodeIsConflictNotServerError() throws Exception {
        create(code, category, "제목", "본문", false).andExpect(status().isCreated());

        // 쓰고 있는 코드 (앞뒤 공백은 지우고 비교)
        create(" " + code + " ", category, "다른 제목", "본문", false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_GUIDE_CODE"));

        // 삭제한 생활수칙의 코드 (DB unique 위반 500 이 아니라 409)
        mockMvc.perform(delete("/api/admin/rules/{code}", code).cookie(admin).with(csrf()))
                .andExpect(status().isNoContent());
        create(code, category, "다시 등록", "본문", false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_GUIDE_CODE"));

        // 체크리스트 항목의 코드
        create("CL-01", category, "제목", "본문", false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_GUIDE_CODE"));
    }

    @Test
    void requiredFieldsAreValidated() throws Exception {
        // 본문 필수 (RUL-09)
        mockMvc.perform(post("/api/admin/rules").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"category\":\"" + category + "\",\"title\":\"제목\"}"))
                .andExpect(status().isBadRequest());
        create(code, category, "제목", "   ", false).andExpect(status().isBadRequest());
        create(code, " ", "제목", "본문", false).andExpect(status().isBadRequest());
        create(code, category, "", "본문", false).andExpect(status().isBadRequest());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM guide WHERE code = ?", Integer.class, code))
                .isZero();

        // 수정도 같은 규칙 (RUL-10). 카테고리는 생략할 수 있지만 빈 값은 안 된다
        create(code, category, "제목", "본문", false).andExpect(status().isCreated());
        update(code, null, "제목", "").andExpect(status().isBadRequest());
        update(code, "  ", "제목", "본문").andExpect(status().isBadRequest());
    }

    @Test
    void rulesAdminApisAreForAdminsOnly() throws Exception {
        mockMvc.perform(get("/api/admin/rules").cookie(user)).andExpect(status().isForbidden());
        create(code, category, "제목", "본문", false, user).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/rules")).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- helpers

    private ResultActions create(String code, String category, String title, String content, boolean faq) throws Exception {
        return create(code, category, title, content, faq, admin);
    }

    private ResultActions create(String code, String category, String title, String content, boolean faq,
                                 Cookie session) throws Exception {
        return mockMvc.perform(post("/api/admin/rules").cookie(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"code":"%s","category":"%s","sortOrder":1,"faq":%s,
                         "title":"%s","content":"%s","docs":["봉투"]}
                        """.formatted(code, category, faq, title, content)));
    }

    private ResultActions update(String code, String category, String title, String content) throws Exception {
        String categoryJson = category == null ? "" : "\"category\":\"" + category + "\",";
        return mockMvc.perform(put("/api/admin/rules/{code}", code).cookie(admin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{" + categoryJson + "\"title\":\"" + title + "\",\"content\":\"" + content
                        + "\",\"noticeChanged\":false}"));
    }

    private Cookie signup(String email) throws Exception {
        redisTemplate.opsForValue().set(SignupVerificationService.VERIFIED_KEY + email, "1");
        return mockMvc.perform(post("/api/auth/signup").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                                + "\",\"passwordConfirm\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getCookie("SIOT_SESSION");
    }

    private Cookie login(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("SIOT_SESSION");
    }

    private static String newEmail() {
        return "r" + ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L) + "@example.com";
    }
}
