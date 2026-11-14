package com.siot.backend.admin;

import com.jayway.jsonpath.JsonPath;
import com.siot.backend.TestcontainersConfiguration;
import com.siot.backend.auth.service.SignupVerificationService;
import com.siot.backend.member.service.AdminBootstrap;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADM-01 관리자 지정·권한 변경, ADM-08 부정 평가·미응답 질문 관리 통합 테스트.
 */
@SpringBootTest(properties = "siot.admin.emails=Boss@Example.com, second-admin@example.com")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminIntegrationTest {

    private static final String ADMIN_EMAIL = "boss@example.com";
    private static final String PASSWORD = "abcd1234";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    AdminBootstrap adminBootstrap;

    @BeforeEach
    void ensureAdminExists() throws Exception {
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM app_user WHERE email = ?", Integer.class, ADMIN_EMAIL);
        if (exists == 0) {
            signup(ADMIN_EMAIL);
        }
        jdbcTemplate.update("UPDATE app_user SET role = 'ADMIN' WHERE email = ?", ADMIN_EMAIL);
    }

    // ================================================================ ADM-01

    @Test
    void configuredEmailBecomesAdminOnSignup() throws Exception {
        Cookie session = signup("second-admin@example.com");
        mockMvc.perform(get("/api/members/me").cookie(session))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
        mockMvc.perform(get("/api/admin/members").cookie(session)).andExpect(status().isOk());

        // 목록에 없는 이메일은 일반 회원
        Cookie user = signup(newEmail());
        mockMvc.perform(get("/api/members/me").cookie(user))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    void bootstrapPromotesExistingConfiguredMember() {
        jdbcTemplate.update("UPDATE app_user SET role = 'USER' WHERE email = ?", ADMIN_EMAIL);

        assertThat(adminBootstrap.promoteConfiguredAdmins()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT role FROM app_user WHERE email = ?", String.class, ADMIN_EMAIL)).isEqualTo("ADMIN");
        // 이미 관리자면 다시 바꾸지 않는다
        assertThat(adminBootstrap.promoteConfiguredAdmins()).isZero();
    }

    @Test
    void adminApisAreForAdminsOnly() throws Exception {
        Cookie user = signup(newEmail());
        mockMvc.perform(get("/api/admin/members").cookie(user)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/chat/reviews").cookie(user)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/members")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminChangesRoleAndTargetMustLogInAgain() throws Exception {
        Cookie admin = login(ADMIN_EMAIL);
        String email = newEmail();
        Cookie oldSession = signup(email);
        long memberId = memberId(email);

        changeRole(admin, memberId, "ADMIN")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));

        // 기존 세션은 끊기고, 다시 로그인하면 관리자 권한이 적용된다
        mockMvc.perform(get("/api/members/me").cookie(oldSession)).andExpect(status().isUnauthorized());
        Cookie newSession = login(email);
        mockMvc.perform(get("/api/admin/members").cookie(newSession)).andExpect(status().isOk());

        // 다시 일반 회원으로
        changeRole(admin, memberId, "USER").andExpect(jsonPath("$.data.role").value("USER"));
        mockMvc.perform(get("/api/admin/members").cookie(newSession)).andExpect(status().isUnauthorized());
    }

    @Test
    void adminCannotDemoteSelfAndUnknownMemberIs404() throws Exception {
        Cookie admin = login(ADMIN_EMAIL);
        changeRole(admin, memberId(ADMIN_EMAIL), "USER")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ADMIN_SELF_DEMOTION"));
        changeRole(admin, 999_999_999L, "ADMIN")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    @Test
    void membersCanBeSearchedByEmailAndRole() throws Exception {
        Cookie admin = login(ADMIN_EMAIL);
        String email = newEmail();
        signup(email);

        mockMvc.perform(get("/api/admin/members").param("email", email.substring(0, 11).toUpperCase()).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].email").value(email))
                .andExpect(jsonPath("$.data.content[0].role").value("USER"));

        String admins = mockMvc.perform(get("/api/admin/members").param("role", "ADMIN").param("size", "100")
                        .cookie(admin))
                .andReturn().getResponse().getContentAsString();
        List<String> roles = JsonPath.read(admins, "$.data.content[*].role");
        List<String> emails = JsonPath.read(admins, "$.data.content[*].email");
        assertThat(roles).isNotEmpty().allMatch("ADMIN"::equals);
        assertThat(emails).contains(ADMIN_EMAIL).doesNotContain(email);
    }

    // ================================================================ ADM-08

    @Test
    void negativeAndNoEvidenceAnswersAreListedAndCanBeReviewed() throws Exception {
        Cookie admin = login(ADMIN_EMAIL);
        String email = newEmail();
        signup(email);
        long conversationId = jdbcTemplate.queryForObject(
                "INSERT INTO chat_conversation (user_id, language) VALUES (?, 'en') RETURNING conversation_id",
                Long.class, memberId(email));
        long negative = qa(conversationId, "How do I pay car tax?", "Pay it in June.", "ANSWERED", false,
                "This is wrong [전화번호]");
        long noEvidence = qa(conversationId, "Where is the best restaurant?", "I couldn't find this.", "NO_EVIDENCE",
                null, null);
        long good = qa(conversationId, "When is resident tax due?", "In August.", "ANSWERED", true, null);

        // 전체: 부정 평가 + 근거 없음 (도움이 된 답변은 제외)
        List<Integer> all = reviewIds(admin, "ALL", null);
        assertThat(all).contains((int) negative, (int) noEvidence).doesNotContain((int) good);

        mockMvc.perform(get("/api/admin/chat/reviews").param("type", "NEGATIVE").param("size", "100").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.messageId == %d)].reason".formatted(negative)).value("NEGATIVE"))
                .andExpect(jsonPath("$.data.content[?(@.messageId == %d)].question".formatted(negative))
                        .value("How do I pay car tax?"))
                .andExpect(jsonPath("$.data.content[?(@.messageId == %d)].feedbackComment".formatted(negative))
                        .value("This is wrong [전화번호]"))
                .andExpect(jsonPath("$.data.content[?(@.messageId == %d)].language".formatted(negative)).value("en"));
        assertThat(reviewIds(admin, "NEGATIVE", null)).doesNotContain((int) noEvidence);
        assertThat(reviewIds(admin, "NO_EVIDENCE", null)).contains((int) noEvidence).doesNotContain((int) negative);

        // 처리 완료 기록
        long adminId = memberId(ADMIN_EMAIL);
        mockMvc.perform(patch("/api/admin/chat/reviews/{id}", noEvidence).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewed\":true,\"adminMemo\":\"맛집 안내는 서비스 범위 밖\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewed").value(true))
                .andExpect(jsonPath("$.data.adminMemo").value("맛집 안내는 서비스 범위 밖"))
                .andExpect(jsonPath("$.data.reviewedBy").value(adminId))
                .andExpect(jsonPath("$.data.reviewedAt").exists());

        // 미처리만 보기에서 빠지고, 처리 완료만 보기에 나온다
        assertThat(reviewIds(admin, "ALL", false)).contains((int) negative).doesNotContain((int) noEvidence);
        assertThat(reviewIds(admin, "ALL", true)).contains((int) noEvidence);

        // 검토 대상이 아닌 답변
        mockMvc.perform(patch("/api/admin/chat/reviews/{id}", good).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewed\":true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT_REVIEW_NOT_FOUND"));
    }

    // ================================================================ helpers

    private long qa(long conversationId, String question, String answer, String status, Boolean helpful,
                    String comment) {
        jdbcTemplate.update("INSERT INTO chat_message (conversation_id, role, content) VALUES (?, 'USER', ?)",
                conversationId, question);
        return jdbcTemplate.queryForObject("""
                INSERT INTO chat_message (conversation_id, role, content, answer_status, is_helpful, feedback_comment)
                VALUES (?, 'ASSISTANT', ?, ?, ?, ?) RETURNING message_id""",
                Long.class, conversationId, answer, status, helpful, comment);
    }

    private List<Integer> reviewIds(Cookie admin, String type, Boolean reviewed) throws Exception {
        var request = get("/api/admin/chat/reviews").param("type", type).param("size", "100").cookie(admin);
        if (reviewed != null) {
            request = request.param("reviewed", reviewed.toString());
        }
        String body = mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.content[*].messageId");
    }

    // ================================================================ NTF-07 안내 내용 변경

    @Test
    void savingGuideTextRecordsNoticeChangedOnlyForRealChanges() throws Exception {
        Cookie admin = login(ADMIN_EMAIL);
        String code = "T-" + ThreadLocalRandom.current().nextInt(100_000, 999_999);
        jdbcTemplate.update("INSERT INTO guide (code, kind) VALUES (?, 'CHECKLIST')", code);
        long guideId = jdbcTemplate.queryForObject("SELECT guide_id FROM guide WHERE code = ?", Long.class, code);
        jdbcTemplate.update("INSERT INTO guide_translation (guide_id, language, title) VALUES (?, 'ko', '처음 제목')",
                guideId);

        mockMvc.perform(get("/api/admin/guides/{code}/translations/ko", code).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("처음 제목"))
                .andExpect(jsonPath("$.data.noticeChangedAt").doesNotExist());

        // 단순 수정: updated_at 만 바뀌고 notice_changed_at 은 그대로 NULL
        saveGuide(admin, code, "ko", "오타 고친 제목", false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("오타 고친 제목"))
                .andExpect(jsonPath("$.data.docs[0]").value("여권"))
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.noticeChangedAt").doesNotExist());
        assertThat(jdbcTemplate.queryForObject("SELECT notice_changed_at IS NULL FROM guide WHERE guide_id = ?",
                Boolean.class, guideId)).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT updated_by FROM guide WHERE guide_id = ?",
                Long.class, guideId)).isEqualTo(memberId(ADMIN_EMAIL));

        // 실제 안내 내용 변경: notice_changed_at 기록
        saveGuide(admin, code, "ko", "기한이 바뀐 제목", true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.noticeChangedAt").isNotEmpty());
        assertThat(jdbcTemplate.queryForObject("SELECT notice_changed_at IS NOT NULL FROM guide WHERE guide_id = ?",
                Boolean.class, guideId)).isTrue();

        // 없던 언어는 새로 만든다
        mockMvc.perform(get("/api/admin/guides/{code}/translations/en", code).cookie(admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GUIDE_TRANSLATION_NOT_FOUND"));
        saveGuide(admin, code, "en", "English title", false).andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT title FROM guide_translation WHERE guide_id = ? AND language = 'en'",
                String.class, guideId)).isEqualTo("English title");
    }

    @Test
    void savingGuideTextValidatesInput() throws Exception {
        Cookie admin = login(ADMIN_EMAIL);
        saveGuide(admin, "NO-SUCH-CODE", "ko", "제목", false)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GUIDE_NOT_FOUND"));
        saveGuide(admin, "CL-01", "ja", "제목", false).andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/admin/guides/CL-01/translations/ko").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"제목\"}"))   // noticeChanged 없음
                .andExpect(status().isBadRequest());

        Cookie user = signup(newEmail());
        saveGuide(user, "CL-01", "ko", "제목", false).andExpect(status().isForbidden());
    }

    private ResultActions saveGuide(Cookie session, String code, String language, String title,
                                    boolean noticeChanged) throws Exception {
        return mockMvc.perform(put("/api/admin/guides/{code}/translations/{language}", code, language)
                .cookie(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + title + "\",\"docs\":[\"여권\",\" 사진 \"],\"fee\":\"30,000원\","
                        + "\"noticeChanged\":" + noticeChanged + "}"));
    }

    private ResultActions changeRole(Cookie admin, long memberId, String role) throws Exception {
        return mockMvc.perform(put("/api/admin/members/{id}/role", memberId).cookie(admin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"" + role + "\"}"));
    }

    private long memberId(String email) {
        return jdbcTemplate.queryForObject("SELECT user_id FROM app_user WHERE email = ?", Long.class, email);
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

    private String newEmail() {
        return "a" + ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L) + "@example.com";
    }
}
