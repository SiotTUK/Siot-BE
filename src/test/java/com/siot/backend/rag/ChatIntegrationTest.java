package com.siot.backend.rag;

import com.siot.backend.TestcontainersConfiguration;
import com.siot.backend.auth.service.SignupVerificationService;
import com.siot.backend.rag.ai.AiUnavailableException;
import com.siot.backend.rag.ai.AnswerGenerator;
import com.siot.backend.rag.ai.QueryEmbedder;
import com.siot.backend.rag.ai.RetrievedChunk;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 챗봇 CB-03·05·12·13·14·16·17 통합 테스트.
 * Gemini 대신 가짜 임베딩(축 방향 단위 벡터)과 가짜 답변 생성기를 쓰고, 검색은 실제 pgvector 로 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ChatIntegrationTest {

    private static final int DIM = 768;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    StringRedisTemplate redisTemplate;

    @MockitoBean
    QueryEmbedder queryEmbedder;

    @MockitoBean
    AnswerGenerator answerGenerator;

    /** 축 번호 (외국인등록 조각 = 0, 자동차세 조각 = 1, 이전 버전 조각 = 2) */
    private static final int AXIS_REGISTRATION = 0;
    private static final int AXIS_CAR_TAX = 1;
    private static final int AXIS_OLD_VERSION = 2;
    private static final int AXIS_UNRELATED = 10;

    @BeforeEach
    void setUp() {
        clearInvocations(queryEmbedder, answerGenerator);
        seedDocumentsOnce();
    }

    // ---------------------------------------------------------------- CB-16, CB-13, CB-17

    @Test
    void answersFromRetrievedChunksWithSources() throws Exception {
        Cookie session = signup();
        long conversationId = startConversation(session, "ko");
        when(queryEmbedder.embedQuery(anyString())).thenReturn(axis(AXIS_REGISTRATION));
        when(answerGenerator.generate(anyString(), anyString(), anyList()))
                .thenReturn("입국 후 90일 이내에 출입국·외국인관서에서 외국인등록을 해야 해요.");

        ask(session, conversationId, "외국인등록은 언제까지 해야 하나요?")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer.role").value("ASSISTANT"))
                .andExpect(jsonPath("$.data.answer.answerStatus").value("ANSWERED"))
                .andExpect(jsonPath("$.data.answer.content").value("입국 후 90일 이내에 출입국·외국인관서에서 외국인등록을 해야 해요."))
                .andExpect(jsonPath("$.data.answer.sources[0].order").value(1))
                .andExpect(jsonPath("$.data.answer.sources[0].documentName").value("시흥시 외국인주민 생활안내"))
                .andExpect(jsonPath("$.data.answer.sources[0].pageStart").value(12))
                .andExpect(jsonPath("$.data.answer.sources[0].sourceUrl").value("https://example.org/guide.pdf"))
                .andExpect(jsonPath("$.data.masked").value(false));

        // 가장 관련 높은 조각이 근거로 넘어가고, 이전 버전·비활성 문서 조각은 넘어가지 않는다
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RetrievedChunk>> context = ArgumentCaptor.forClass(List.class);
        verify(answerGenerator).generate(eq("외국인등록은 언제까지 해야 하나요?"), eq("ko"), context.capture());
        assertThat(context.getValue()).hasSize(1);
        assertThat(context.getValue().get(0).content()).contains("외국인등록");

        // 대화 내용 조회에도 근거가 포함된다 (CB-03, CB-17)
        mockMvc.perform(get("/api/chat/conversations/{id}/messages", conversationId).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].role").value("USER"))
                .andExpect(jsonPath("$.data[1].sources[0].documentName").value("시흥시 외국인주민 생활안내"))
                .andExpect(jsonPath("$.data[1].sources[0].pageEnd").value(13));
    }

    // ---------------------------------------------------------------- CB-12

    @Test
    void noEvidenceWhenRelevanceBelowThreshold() throws Exception {
        Cookie session = signup();
        long conversationId = startConversation(session, "en");
        when(queryEmbedder.embedQuery(anyString())).thenReturn(axis(AXIS_UNRELATED));

        ask(session, conversationId, "What is the best restaurant in Siheung?")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer.answerStatus").value("NO_EVIDENCE"))
                .andExpect(jsonPath("$.data.answer.content").value(org.hamcrest.Matchers.containsString("1345")))
                .andExpect(jsonPath("$.data.answer.sources.length()").value(0));

        // 근거가 없으면 Gemini 를 호출하지 않는다
        verify(answerGenerator, never()).generate(anyString(), anyString(), anyList());
    }

    @Test
    void noEvidenceWhenModelSaysNoAnswer() throws Exception {
        Cookie session = signup();
        long conversationId = startConversation(session, "ko");
        when(queryEmbedder.embedQuery(anyString())).thenReturn(axis(AXIS_CAR_TAX));
        when(answerGenerator.generate(anyString(), anyString(), anyList())).thenReturn(AnswerGenerator.NO_ANSWER);

        ask(session, conversationId, "자동차세를 카드로 낼 수 있나요?")
                .andExpect(jsonPath("$.data.answer.answerStatus").value("NO_EVIDENCE"))
                .andExpect(jsonPath("$.data.answer.content").value(org.hamcrest.Matchers.startsWith("자료에서 찾지 못했어요")))
                .andExpect(jsonPath("$.data.answer.sources.length()").value(0));
    }

    // ---------------------------------------------------------------- CB-02 (오류 안내)

    @Test
    void aiFailureReturnsGuidanceInsteadOfError() throws Exception {
        Cookie session = signup();
        long conversationId = startConversation(session, "vi");
        when(queryEmbedder.embedQuery(anyString())).thenThrow(new AiUnavailableException("quota"));

        ask(session, conversationId, "Thuế ô tô nộp khi nào?")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer.answerStatus").value("ERROR"))
                .andExpect(jsonPath("$.data.answer.content").value(org.hamcrest.Matchers.containsString("1345")));
    }

    // ---------------------------------------------------------------- CB-05

    @Test
    void personalNumbersAreMaskedBeforeAiAndStorage() throws Exception {
        Cookie session = signup();
        long conversationId = startConversation(session, "ko");
        when(queryEmbedder.embedQuery(anyString())).thenReturn(axis(AXIS_REGISTRATION));
        when(answerGenerator.generate(anyString(), anyString(), anyList())).thenReturn("등록증 재발급 안내입니다.");

        ask(session, conversationId, "제 등록번호 900101-5123456, 전화 010-1234-5678 인데 재발급 되나요?")
                .andExpect(jsonPath("$.data.masked").value(true))
                .andExpect(jsonPath("$.data.question.content").value("제 등록번호 [등록번호], 전화 [전화번호] 인데 재발급 되나요?"));

        String masked = "제 등록번호 [등록번호], 전화 [전화번호] 인데 재발급 되나요?";
        verify(queryEmbedder).embedQuery(masked);
        verify(answerGenerator).generate(eq(masked), any(), anyList());
        Integer leaked = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM chat_message WHERE content LIKE '%5123456%' OR content LIKE '%1234-5678%'",
                Integer.class);
        assertThat(leaked).isZero();
    }

    // ---------------------------------------------------------------- CB-03, NFR-03

    @Test
    void conversationsAreListedNewestFirstAndOwnerOnly() throws Exception {
        Cookie owner = signup();
        long first = startConversation(owner, "ko");
        long second = startConversation(owner, "ko");
        when(queryEmbedder.embedQuery(anyString())).thenReturn(axis(AXIS_UNRELATED));
        ask(owner, first, "첫 번째 대화 질문").andExpect(status().isOk());

        // 방금 질문한 대화가 맨 위 (최근 대화 순), 제목 = 첫 질문
        mockMvc.perform(get("/api/chat/conversations").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].conversationId").value(first))
                .andExpect(jsonPath("$.data.content[0].title").value("첫 번째 대화 질문"))
                .andExpect(jsonPath("$.data.content[1].conversationId").value(second))
                .andExpect(jsonPath("$.data.content[1].title").doesNotExist());

        // 다른 회원은 볼 수도, 질문할 수도 없다 (존재 여부도 숨김)
        Cookie other = signup();
        mockMvc.perform(get("/api/chat/conversations/{id}/messages", first).cookie(other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT_CONVERSATION_NOT_FOUND"));
        ask(other, first, "남의 대화에 질문").andExpect(status().isNotFound());
        mockMvc.perform(get("/api/chat/conversations").cookie(other))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void conversationUsesMemberLanguageByDefaultAndValidatesQuestion() throws Exception {
        Cookie session = signup();
        MvcResult result = mockMvc.perform(post("/api/chat/conversations").cookie(session).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.language").value("ko"))
                .andReturn();
        long id = Long.parseLong(com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.conversationId").toString());

        ask(session, id, "   ").andExpect(status().isBadRequest());
        ask(session, id, "가".repeat(1001))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CHAT_QUESTION_LENGTH"));
    }

    @Test
    void chatRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/chat/conversations")).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- helpers

    private static boolean seeded;

    /** 현재 버전 문서 2조각 + 이전 버전 1조각 + 비활성 문서 1조각 */
    private void seedDocumentsOnce() {
        if (seeded && count("document WHERE name = '시흥시 외국인주민 생활안내'") > 0) {
            return;
        }
        Long doc = jdbcTemplate.queryForObject("""
                INSERT INTO document (name, source_url) VALUES ('시흥시 외국인주민 생활안내', 'https://example.org/guide.pdf')
                RETURNING document_id""", Long.class);
        Long oldVersion = insertVersion(doc, 1, false);
        Long current = insertVersion(doc, 2, true);
        insertChunk(current, 1, 12, 13, "외국인등록은 입국일부터 90일 이내에 출입국·외국인관서에서 신청합니다.", AXIS_REGISTRATION);
        insertChunk(current, 2, 30, 30, "자동차세는 매년 6월과 12월에 납부합니다.", AXIS_CAR_TAX);
        insertChunk(oldVersion, 1, 5, 5, "이전 버전의 오래된 안내입니다.", AXIS_OLD_VERSION);

        Long inactive = jdbcTemplate.queryForObject("""
                INSERT INTO document (name, is_active) VALUES ('사용 중지된 문서', false) RETURNING document_id""", Long.class);
        insertChunk(insertVersion(inactive, 1, true), 1, 1, 1, "사용 중지된 문서의 외국인등록 안내", AXIS_REGISTRATION);
        seeded = true;
    }

    private Long insertVersion(Long documentId, int versionNo, boolean current) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO document_version (document_id, version_no, file_storage_key, processing_status, is_current)
                VALUES (?, ?, 'test/key.pdf', 'COMPLETED', ?) RETURNING document_version_id""",
                Long.class, documentId, versionNo, current);
    }

    private void insertChunk(Long versionId, int chunkNo, int pageStart, int pageEnd, String content, int axis) {
        jdbcTemplate.update("""
                INSERT INTO document_chunk (document_version_id, chunk_no, page_start, page_end, content, embedding)
                VALUES (?, ?, ?, ?, ?, CAST(? AS vector))""",
                versionId, chunkNo, pageStart, pageEnd, content, literal(axis(axis)));
    }

    private static float[] axis(int index) {
        float[] v = new float[DIM];
        v[index] = 1f;
        return v;
    }

    private static String literal(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            sb.append(i == 0 ? "" : ",").append(v[i]);
        }
        return sb.append(']').toString();
    }

    private int count(String fromWhere) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + fromWhere, Integer.class);
    }

    private Cookie signup() throws Exception {
        String email = "c" + ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L) + "@example.com";
        redisTemplate.opsForValue().set(SignupVerificationService.VERIFIED_KEY + email, "1");
        MvcResult result = mockMvc.perform(post("/api/auth/signup").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"abcd1234","passwordConfirm":"abcd1234"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        return result.getResponse().getCookie("SIOT_SESSION");
    }

    private long startConversation(Cookie session, String language) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/chat/conversations").cookie(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"%s\"}".formatted(language)))
                .andExpect(status().isCreated())
                .andReturn();
        return Long.parseLong(com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.conversationId").toString());
    }

    private ResultActions ask(Cookie session, long conversationId, String question) throws Exception {
        return mockMvc.perform(post("/api/chat/conversations/{id}/messages", conversationId)
                .cookie(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"" + question.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}"));
    }
}
