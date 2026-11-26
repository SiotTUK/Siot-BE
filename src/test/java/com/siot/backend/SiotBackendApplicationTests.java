package com.siot.backend;

import com.siot.backend.global.config.SchedulingProperties;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.support.CronExpression;

import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.SessionRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 인프라 연결 검증: Flyway 마이그레이션 + JPA 스키마 검증(ddl-auto=validate), pgvector, Redis, Security, Actuator.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SiotBackendApplicationTests {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    RedisConnectionFactory redisConnectionFactory;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    SessionRepository<?> sessionRepository;

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    SchedulingProperties schedulingProperties;

    @Test
    void contextLoads() {
        // 컨텍스트 로딩 시 Flyway 적용 후 Entity-스키마 일치 여부를 validate 한다.
    }

    @Test
    void flywayAppliedAllMigrations() {
        Integer applied = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = true", Integer.class);
        assertThat(applied).isEqualTo(19);
    }

    @Test
    void checklistRuleItemsAreSeeded() {
        // 체크리스트 규칙표: 필수 11 · 안내 1 · 선택 3, 생활 변화 할 일 LC-01 ~ LC-08
        assertThat(count("guide WHERE kind = 'CHECKLIST' AND item_type = 'REQUIRED'")).isEqualTo(11);
        assertThat(count("guide WHERE kind = 'CHECKLIST' AND item_type = 'INFO'")).isEqualTo(1);
        assertThat(count("guide WHERE kind = 'CHECKLIST' AND item_type = 'OPTIONAL'")).isEqualTo(3);
        assertThat(count("guide WHERE kind = 'LIFE_CHANGE_TASK'")).isEqualTo(16);
        assertThat(count("guide g WHERE NOT EXISTS (SELECT 1 FROM guide_translation t "
                + "WHERE t.guide_id = g.guide_id AND t.language = 'ko')")).isZero();
        assertThat(count("guide_variant_translation v JOIN guide g ON g.guide_id = v.guide_id "
                + "WHERE g.code = 'CL-04' AND v.language = 'ko'")).isEqualTo(5);
    }

    @Test
    void schedulingEnabledWithConfiguredJobs() {
        // 아키텍처 "예약 작업": EVE-01 매일 새벽 3시 수집, NTF-05 예약 발송
        assertThat(applicationContext.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class)).isNotEmpty();
        assertThat(schedulingProperties.zone()).isEqualTo("Asia/Seoul");
        assertThat(schedulingProperties.eventCollectCron()).isEqualTo("0 0 3 * * *");
        assertThat(CronExpression.parse(schedulingProperties.eventCollectCron())
                .next(LocalDateTime.of(2026, 1, 1, 12, 0)))
                .isEqualTo(LocalDateTime.of(2026, 1, 2, 3, 0));
        assertThat(schedulingProperties.notificationDispatchInterval()).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void pgvectorExtensionEnabled() {
        String ext = jdbcTemplate.queryForObject(
                "SELECT extname FROM pg_extension WHERE extname = 'vector'", String.class);
        assertThat(ext).isEqualTo("vector");
    }

    @Test
    void redisReachable() {
        try (var connection = redisConnectionFactory.getConnection()) {
            assertThat(connection.ping()).isEqualTo("PONG");
        }
    }

    @Test
    void swaggerDocsArePublicAndListApis() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("웰컴시흥 (Siot) Backend API"))
                .andExpect(jsonPath("$.components.securitySchemes.session.name").value("SIOT_SESSION"))
                .andExpect(jsonPath("$.paths['/api/auth/login']").exists())
                .andExpect(jsonPath("$.paths['/api/admin/guides/{code}/translations/{language}'].put").exists());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    void swaggerDocsMatchActualBehavior() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                // CsrfToken 인자는 파라미터로 나오지 않는다
                .andExpect(jsonPath("$.paths['/api/auth/csrf'].get.parameters").doesNotExist())
                // 실제 성공 코드 (201·202·204)
                .andExpect(jsonPath("$.paths['/api/auth/signup'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/signup'].post.responses['200']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/auth/signup/email-verification'].post.responses['202']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/logout'].post.responses['204'].content").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/chat/conversations'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/members/me/checklist/{code}'].put.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/members/me/checklist/{code}'].put.responses['200']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/members/me/life-changes/{code}'].delete.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/members/me/notifications/read-all'].patch.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/reports'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/reports'].post.responses['200']").doesNotExist())
                // 공개 API 는 인증 표시 없음, 그 외는 세션 + 401
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.responses['401'].content['application/json'].examples.LOGIN_FAILED").exists())
                .andExpect(jsonPath("$.paths['/api/members/me'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/members/me'].get.responses['401'].content['application/json'].examples.UNAUTHORIZED").exists())
                // 관리자 403, 변경 요청 CSRF 403, API 별 오류 코드
                .andExpect(jsonPath("$.paths['/api/admin/members'].get.responses['403'].content['application/json'].examples.FORBIDDEN").exists())
                .andExpect(jsonPath("$.paths['/api/chat/conversations/{conversationId}/messages'].post.responses['403'].content['application/json'].examples.CSRF_TOKEN_INVALID").exists())
                .andExpect(jsonPath("$.paths['/api/auth/signup'].post.responses['409'].content['application/json'].examples.DUPLICATE_EMAIL").exists())
                .andExpect(jsonPath("$.paths['/api/rules/{code}'].get.responses['404'].content['application/json'].examples.GUIDE_NOT_FOUND").exists())
                .andExpect(jsonPath("$.paths['/api/members/me/notifications/{notificationId}/read'].patch.responses['404'].content['application/json'].examples.NOTIFICATION_NOT_FOUND").exists())
                .andExpect(jsonPath("$.paths['/api/admin/reports/{reportId}'].patch.responses['404'].content['application/json'].examples.CONTENT_REPORT_NOT_FOUND").exists())
                .andExpect(jsonPath("$.components.schemas.ErrorResponse.properties.code.enum").isNotEmpty())
                // 설명·태그
                .andExpect(jsonPath("$.paths['/api/auth/signup'].post.summary").value("M-01~M-03 회원가입"))
                .andExpect(jsonPath("$.paths['/api/auth/signup'].post.tags[0]").value("1. 인증·가입"))
                .andExpect(jsonPath("$.paths['/api/members/me/checklist'].get.tags[0]").value("5. 체크리스트"))
                .andExpect(jsonPath("$.paths['/api/reports'].post.tags[0]").value("8-2. 오류 신고"))
                .andExpect(jsonPath("$.paths['/api/admin/reports'].get.tags[0]").value("9-4. 관리자: 오류 신고"))
                .andExpect(jsonPath("$.components.schemas.ReferralContact").exists())
                .andExpect(jsonPath("$.components.schemas.Contact").doesNotExist());
    }

    @Test
    void healthEndpointIsPublicAndUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void protectedEndpointReturns401Json() throws Exception {
        mockMvc.perform(get("/api/members/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void contentRequiresLogin() throws Exception {
        // 회원가입하지 않으면 아무것도 못 본다 (11/13 결정)
        for (String path : new String[]{"/api/events", "/api/checklists", "/api/home", "/api/chat/conversations"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
    }

    @Test
    void adminApiRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/admin/events"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postWithoutCsrfTokenIsRejectedWithClearCode() throws Exception {
        mockMvc.perform(post("/api/reports"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
    }

    @Test
    void csrfEndpointIssuesTokenUsableInHeader() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andReturn();
        String token = result.getResponse().getCookie("XSRF-TOKEN").getValue();

        // 토큰을 쿠키 + 헤더로 보내면 CSRF 를 통과한다 (가입·로그인 API 는 공개, 본문이 없어 400)
        mockMvc.perform(post("/api/auth/login")
                        .cookie(new Cookie("XSRF-TOKEN", token))
                        .header("X-XSRF-TOKEN", token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void metricsEndpointIsNotPublic() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sessionRepositorySupportsLookupByUser() {
        // M-07: 탈퇴 시 회원의 모든 세션 삭제에 필요
        assertThat(sessionRepository).isInstanceOf(FindByIndexNameSessionRepository.class);
    }

    @Test
    @Transactional
    void deletingMemberRemovesOwnedDataAndKeepsHistory() {
        // M-07: 탈퇴 시 회원 정보·온보딩 답·완료 기록·생활 변화 등 물리 삭제
        Long userId = jdbcTemplate.queryForObject("""
                INSERT INTO app_user (email, password_hash, user_type, purpose)
                VALUES ('deltest01@example.com', 'x', 'NEW', 'WORK') RETURNING user_id""", Long.class);
        Long guideId = jdbcTemplate.queryForObject("""
                INSERT INTO guide (code, kind, created_by) VALUES ('G-DEL', 'CHECKLIST', ?)
                RETURNING guide_id""", Long.class, userId);
        jdbcTemplate.update(
                "INSERT INTO checklist_progress (user_id, guide_id, status) VALUES (?, ?, 'DONE')", userId, guideId);
        jdbcTemplate.update("INSERT INTO member_residence (user_id, residence) VALUES (?, 'KIDS')", userId);
        jdbcTemplate.update("""
                INSERT INTO member_life_change (user_id, life_change_code, base_date)
                VALUES (?, 'LC-01', DATE '2026-11-01')""", userId);
        Long conversationId = jdbcTemplate.queryForObject(
                "INSERT INTO chat_conversation (user_id) VALUES (?) RETURNING conversation_id", Long.class, userId);
        jdbcTemplate.update(
                "INSERT INTO chat_message (conversation_id, role, content) VALUES (?, 'USER', 'hi')", conversationId);

        jdbcTemplate.update("DELETE FROM app_user WHERE user_id = ?", userId);

        assertThat(count("checklist_progress WHERE user_id = " + userId)).isZero();
        assertThat(count("member_residence WHERE user_id = " + userId)).isZero();
        assertThat(count("member_life_change WHERE user_id = " + userId)).isZero();
        assertThat(count("chat_message WHERE conversation_id = " + conversationId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT created_by FROM guide WHERE guide_id = ?", Long.class, guideId)).isNull();
    }

    @Test
    void onboardingAnswersAreConstrainedToRuleValues() {
        // 트랜잭션 없이 실행: PostgreSQL 은 제약 위반 후 같은 트랜잭션의 다음 문장을 모두 거부한다
        Long userId = jdbcTemplate.queryForObject("""
                INSERT INTO app_user (email, password_hash)
                VALUES ('cktest01@example.com', 'x') RETURNING user_id""", Long.class);
        try {
            assertThatThrownBy(() -> jdbcTemplate.update(
                    "UPDATE app_user SET purpose = 'TRAVEL' WHERE user_id = ?", userId))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbcTemplate.update(
                    "INSERT INTO member_residence (user_id, residence) VALUES (?, 'PET')", userId))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbcTemplate.update(
                    "INSERT INTO member_life_change (user_id, life_change_code, base_date) VALUES (?, 'LC-09', current_date)",
                    userId))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            jdbcTemplate.update("DELETE FROM app_user WHERE user_id = ?", userId);
        }
    }

    @Test
    void checklistProgressIsStoredPerPeriod() {
        // 세금(CL-10)은 회차별, 생활 변화 할 일은 기준일별로 완료 기록이 따로 남는다
        Long userId = jdbcTemplate.queryForObject("""
                INSERT INTO app_user (email, password_hash)
                VALUES ('period01@example.com', 'x') RETURNING user_id""", Long.class);
        try {
            Long tax = jdbcTemplate.queryForObject("SELECT guide_id FROM guide WHERE code = 'CL-10'", Long.class);
            Long move = jdbcTemplate.queryForObject("SELECT guide_id FROM guide WHERE code = 'LC-01-1'", Long.class);
            String insert = "INSERT INTO checklist_progress (user_id, guide_id, period_key, status) VALUES (?, ?, ?, 'DONE')";
            jdbcTemplate.update(insert, userId, tax, "2026-06");
            jdbcTemplate.update(insert, userId, tax, "2026-12");
            jdbcTemplate.update(insert, userId, move, "2026-01-10");
            jdbcTemplate.update(insert, userId, move, "2026-06-01");
            assertThat(count("checklist_progress WHERE user_id = " + userId)).isEqualTo(4);

            assertThatThrownBy(() -> jdbcTemplate.update(insert, userId, tax, "2026/06"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            jdbcTemplate.update("DELETE FROM app_user WHERE user_id = ?", userId);
        }
    }

    @Test
    void notificationTargetAndSourceKeyConstraints() {
        Long userId = jdbcTemplate.queryForObject("""
                INSERT INTO app_user (email, password_hash)
                VALUES ('notif01@example.com', 'x') RETURNING user_id""", Long.class);
        try {
            Long guideId = jdbcTemplate.queryForObject("SELECT guide_id FROM guide WHERE code = 'CL-01'", Long.class);
            Long eventId = jdbcTemplate.queryForObject(
                    "INSERT INTO event (external_source, external_id) VALUES ('test', ?) RETURNING event_id",
                    Long.class, "n-" + userId);
            String insert = """
                    INSERT INTO notification (user_id, kind, guide_id, event_id, source_key, scheduled_at, title, body)
                    VALUES (?, 'DEADLINE', ?, ?, ?, now(), 't', 'b')""";
            jdbcTemplate.update(insert, userId, guideId, null, "CL-01:2027-01-18:D-7");

            // 같은 알림(source_key)은 한 번만
            assertThatThrownBy(() -> jdbcTemplate.update(insert, userId, guideId, null, "CL-01:2027-01-18:D-7"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            // 대상은 하나만
            assertThatThrownBy(() -> jdbcTemplate.update(insert, userId, guideId, eventId, "x"))
                    .isInstanceOf(DataIntegrityViolationException.class);

            // 행사를 지워도 알림 기록은 남고 참조만 비워진다
            jdbcTemplate.update(insert, userId, null, eventId, "EVENT:" + eventId);
            jdbcTemplate.update("DELETE FROM event WHERE event_id = ?", eventId);
            assertThat(count("notification WHERE source_key = 'EVENT:" + eventId + "' AND event_id IS NULL")).isEqualTo(1);
        } finally {
            jdbcTemplate.update("DELETE FROM app_user WHERE user_id = ?", userId);
        }
    }

    @Test
    void timestampsAreStoredWithTimeZone() {
        Integer plain = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema = current_schema() AND data_type = 'timestamp without time zone'
                  AND table_name <> 'flyway_schema_history'""", Integer.class);
        assertThat(plain).isZero();
        assertThat(count("information_schema.tables WHERE table_name = 'refresh_token'")).isZero();
    }

    private int count(String fromWhere) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + fromWhere, Integer.class);
    }
}
