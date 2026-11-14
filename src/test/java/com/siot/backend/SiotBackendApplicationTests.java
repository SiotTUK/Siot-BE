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
        assertThat(applied).isEqualTo(6);
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
        // M-07: 탈퇴 시 회원 정보·온보딩 답·완료 기록·생활 변화·refresh token 등 물리 삭제
        Long userId = jdbcTemplate.queryForObject("""
                INSERT INTO app_user (email, password_hash, display_name, user_type, purpose)
                VALUES ('deltest01@example.com', 'x', 'tester', 'NEW', 'WORK') RETURNING user_id""", Long.class);
        Long guideId = jdbcTemplate.queryForObject("""
                INSERT INTO guide (code, kind, created_by) VALUES ('G-DEL', 'CHECKLIST', ?)
                RETURNING guide_id""", Long.class, userId);
        jdbcTemplate.update(
                "INSERT INTO checklist_progress (user_id, guide_id, status) VALUES (?, ?, 'DONE')", userId, guideId);
        jdbcTemplate.update("INSERT INTO member_residence (user_id, residence) VALUES (?, 'KIDS')", userId);
        jdbcTemplate.update("""
                INSERT INTO member_life_change (user_id, life_change_code, base_date)
                VALUES (?, 'LC-01', DATE '2026-11-01')""", userId);
        jdbcTemplate.update("""
                INSERT INTO refresh_token (user_id, token_hash, expires_at)
                VALUES (?, 'h', now() + interval '1 day')""", userId);
        Long conversationId = jdbcTemplate.queryForObject(
                "INSERT INTO chat_conversation (user_id) VALUES (?) RETURNING conversation_id", Long.class, userId);
        jdbcTemplate.update(
                "INSERT INTO chat_message (conversation_id, role, content) VALUES (?, 'USER', 'hi')", conversationId);

        jdbcTemplate.update("DELETE FROM app_user WHERE user_id = ?", userId);

        assertThat(count("checklist_progress WHERE user_id = " + userId)).isZero();
        assertThat(count("member_residence WHERE user_id = " + userId)).isZero();
        assertThat(count("member_life_change WHERE user_id = " + userId)).isZero();
        assertThat(count("refresh_token WHERE user_id = " + userId)).isZero();
        assertThat(count("chat_message WHERE conversation_id = " + conversationId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT created_by FROM guide WHERE guide_id = ?", Long.class, guideId)).isNull();
    }

    @Test
    void onboardingAnswersAreConstrainedToRuleValues() {
        // 트랜잭션 없이 실행: PostgreSQL 은 제약 위반 후 같은 트랜잭션의 다음 문장을 모두 거부한다
        Long userId = jdbcTemplate.queryForObject("""
                INSERT INTO app_user (email, password_hash, display_name)
                VALUES ('cktest01@example.com', 'x', 'tester') RETURNING user_id""", Long.class);
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

    private int count(String fromWhere) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + fromWhere, Integer.class);
    }
}
