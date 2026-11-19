package com.siot.backend.rag;

import com.siot.backend.TestcontainersConfiguration;
import com.siot.backend.auth.service.SignupVerificationService;
import com.siot.backend.rag.ai.AiUnavailableException;
import com.siot.backend.rag.ai.AnswerGenerator;
import com.siot.backend.rag.ai.ChatTurn;
import com.siot.backend.rag.ai.QueryRewriter;
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

    @MockitoBean
    QueryRewriter queryRewriter;

    /** 축 번호 (외국인등록 조각 = 0, 자동차세 조각 = 1, 이전 버전 조각 = 2) */
    private static final int AXIS_REGISTRATION = 0;
    private static final int AXIS_CAR_TAX = 1;
    private static final int AXIS_OLD_VERSION = 2;
    private static final int AXIS_UNRELATED = 10;

    @BeforeEach
    void setUp() {
        clearInvocations(queryEmbedder, answerGenerator, queryRewriter);
        seedDocumentsOnce();
    }

    // ---------------------------------------------------------------- CB-16, CB-13, CB-17

    @Test
    void answersFromRetrievedChunksWithSources() throws Exception {
        Cookie session = signup();
        long conversationId = startConversation(session, "ko");
        when(queryEmbedder.embedQuery(anyString())).thenReturn(axis(AXIS_REGISTRATION));
        when(answerGenerator.generate(anyString(), anyString(), anyList(), anyList()))
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
                // CB-10: 근거 쪽(12~13)과 겹치는 항목만 (CL-01). 쪽이 겹치지 않는 CL-05 는 제외
                .andExpect(jsonPath("$.data.answer.relatedPages.length()").value(1))
                .andExpect(jsonPath("$.data.answer.relatedPages[0].type").value("CHECKLIST"))
                .andExpect(jsonPath("$.data.answer.relatedPages[0].code").value("CL-01"))
                .andExpect(jsonPath("$.data.answer.relatedPages[0].title").value("외국인등록"))
                .andExpect(jsonPath("$.data.answer.emergencyContacts.length()").value(0))
                .andExpect(jsonPath("$.data.answer.helpful").doesNotExist())
                .andExpect(jsonPath("$.data.masked").value(false));

        // 가장 관련 높은 조각이 근거로 넘어가고, 이전 버전·반영 중·비활성 문서 조각은 넘어가지 않는다
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RetrievedChunk>> context = ArgumentCaptor.forClass(List.class);
        verify(answerGenerator).generate(eq("외국인등록은 언제까지 해야 하나요?"), eq("ko"), context.capture(), eq(List.of()));
        assertThat(context.getValue()).hasSize(1);
        assertThat(context.getValue().get(0).content()).contains("외국인등록");

        // 대화 내용 조회에도 근거가 포함된다 (CB-03, CB-17)
        mockMvc.perform(get("/api/chat/conversations/{id}/messages", conversationId).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].role").value("USER"))
                .andExpect(jsonPath("$.data[1].sources[0].documentName").value("시흥시 외국인주민 생활안내"))
                .andExpect(jsonPath("$.data[1].sources[0].pageEnd").value(13))
                .andExpect(jsonPath("$.data[1].relatedPages[0].code").value("CL-01"));
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
        verify(answerGenerator, never()).generate(anyString(), anyString(), anyList(), anyList());
    }

    @Test
    void noEvidenceWhenModelSaysNoAnswer() throws Exception {
        Cookie session = signup();
        long conversationId = startConversation(session, "ko");
        when(queryEmbedder.embedQuery(anyString())).thenReturn(axis(AXIS_CAR_TAX));
        when(answerGenerator.generate(anyString(), anyString(), anyList(), anyList())).thenReturn(AnswerGenerator.NO_ANSWER);

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

    @Test
    void aiFailureStillShowsRelatedPageLinks() throws Exception {
        Cookie session = signup();
        long conversationId = startConversation(session, "ko");

        // 검색은 됐는데 답변 생성이 실패 → 검색된 조각과 연결된 항목 (출처는 보이지 않음)
        when(queryEmbedder.embedQuery(anyString())).thenReturn(axis(AXIS_REGISTRATION));
        when(answerGenerator.generate(anyString(), anyString(), anyList(), anyList()))
                .thenThrow(new AiUnavailableException("quota"));
        ask(session, conversationId, "등록증은 어디서 받아요?")
                .andExpect(jsonPath("$.data.answer.answerStatus").value("ERROR"))
                .andExpect(jsonPath("$.data.answer.sources.length()").value(0))
                .andExpect(jsonPath("$.data.answer.relatedPages[0].code").value("CL-01"));

        // 임베딩부터 실패 → 질문에 제목이 들어 있는 항목
        when(queryEmbedder.embedQuery(anyString())).thenThrow(new AiUnavailableException("quota"));
        ask(session, conversationId, "자동차세는 언제 내요?")
                .andExpect(jsonPath("$.data.answer.answerStatus").value("ERROR"))
                .andExpect(jsonPath("$.data.answer.relatedPages[0].code").value("CL-10"))
                .andExpect(jsonPath("$.data.answer.relatedPages[0].title").value("자동차세 내기"));

        // 대화 기록에서도 같은 링크
        mockMvc.perform(get("/api/chat/conversations/{id}/messages", conversationId).cookie(session))
                .andExpect(jsonPath("$.data[3].relatedPages[0].code").value("CL-10"));
    }

    @Test
    void relatedPagesFallBackToTitleMatchWithoutDocumentLink() throws Exception {
        // CB-10: 문서 연결이 없는 생활수칙도 질문에 제목이 있으면 링크
        String code = "RUL-T" + java.util.concurrent.ThreadLocalRandom.current().nextInt(10_000, 99_999);
        jdbcTemplate.update("INSERT INTO guide (code, kind) VALUES (?, 'RULE')", code);
        jdbcTemplate.update("INSERT INTO guide_translation (guide_id, language, title) "
                + "SELECT guide_id, 'ko', '분리배출 요일표' FROM guide WHERE code = ?", code);
        Cookie session = signup();
        long conversationId = startConversation(session, "ko");
        when(queryEmbedder.embedQuery(anyString())).thenReturn(axis(AXIS_UNRELATED));

        ask(session, conversationId, "우리 동네 분리배출 요일표 알려 주세요")
                .andExpect(jsonPath("$.data.answer.answerStatus").value("NO_EVIDENCE"))
                .andExpect(jsonPath("$.data.answer.relatedPages[0].type").value("RULE"))
                .andExpect(jsonPath("$.data.answer.relatedPages[0].code").value(code));
    }

    @Test
    void eventQuestionLinksUpcomingActiveEvents() throws Exception {
        // CB-10: 행사 질문이면 끝나지 않은 ACTIVE 행사 상세 링크 (지난 행사·삭제된 행사 제외)
        String tag = "시옷T" + ThreadLocalRandom.current().nextInt(10_000, 99_999);
        java.time.LocalDate today = java.time.LocalDate.now(com.siot.backend.global.config.ClockConfig.KST);
        long upcoming = insertEvent("ACTIVE", today.plusDays(10), today.plusDays(10), tag + " 다문화 축제",
                "Multicultural Festival " + tag);
        long ongoing = insertEvent("ACTIVE", today.minusDays(1), today.plusDays(1), tag + " 전통 공연", null);
        insertEvent("ACTIVE", today.minusDays(5), today.minusDays(1), tag + " 지난 축제", null);
        insertEvent("DELETED", today.plusDays(3), today.plusDays(3), tag + " 취소된 축제", null);
        Cookie session = signup();
        when(queryEmbedder.embedQuery(anyString())).thenReturn(axis(AXIS_UNRELATED));

        long ko = startConversation(session, "ko");
        // 제목 단어가 많이 맞는 행사가 먼저, 나머지는 시작일 순
        ask(session, ko, tag + " 다문화 축제는 언제 해요?")
                .andExpect(jsonPath("$.data.answer.relatedPages[0].type").value("EVENT"))
                .andExpect(jsonPath("$.data.answer.relatedPages[0].code").value(String.valueOf(upcoming)))
                .andExpect(jsonPath("$.data.answer.relatedPages[0].title").value(tag + " 다문화 축제"))
                .andExpect(jsonPath("$.data.answer.relatedPages[1].code").value(String.valueOf(ongoing)))
                .andExpect(jsonPath("$.data.answer.relatedPages[?(@.type == 'EVENT')].code")
                        .value(org.hamcrest.Matchers.containsInAnyOrder(String.valueOf(upcoming), String.valueOf(ongoing))));

        // 질문 언어 제목, 번역이 없으면 한국어 제목
        long en = startConversation(session, "en");
        ask(session, en, "When is the Multicultural Festival " + tag + "?")
                .andExpect(jsonPath("$.data.answer.relatedPages[0].code").value(String.valueOf(upcoming)))
                .andExpect(jsonPath("$.data.answer.relatedPages[0].title").value("Multicultural Festival " + tag))
                .andExpect(jsonPath("$.data.answer.relatedPages[1].title").value(tag + " 전통 공연"));

        // 행사 질문이 아니면 행사 링크 없음
        ask(session, ko, tag + " 다문화 가족 지원 알려 주세요")
                .andExpect(jsonPath("$.data.answer.relatedPages[?(@.type == 'EVENT')]").isEmpty());
    }

    private long insertEvent(String status, java.time.LocalDate startsOn, java.time.LocalDate endsOn,
                             String koTitle, String enTitle) {
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO event (status, starts_on, ends_on) VALUES (?, ?, ?) RETURNING event_id",
                Long.class, status, startsOn, endsOn);
        jdbcTemplate.update("INSERT INTO event_translation (language, event_id, title) VALUES ('ko', ?, ?)", id, koTitle);
        if (enTitle != null) {
            jdbcTemplate.update("INSERT INTO event_translation (language, event_id, title) VALUES ('en', ?, ?)", id, enTitle);
        }
        return id;
    }

    @Test
    void answerTimeIsRecorded() throws Exception {
        Cookie session = signup();
        long conversationId = startConversation(session, "ko");
        when(queryEmbedder.embedQuery(anyString())).thenReturn(axis(AXIS_UNRELATED));
        long before = timerCount("NO_EVIDENCE");

        ask(session, conversationId, "아무 질문").andExpect(status().isOk());

        assertThat(timerCount("NO_EVIDENCE")).isEqualTo(before + 1);
    }

    @Autowired
    io.micrometer.core.instrument.MeterRegistry meterRegistry;

    private long timerCount(String status) {
        io.micrometer.core.instrument.Timer timer = meterRegistry.find("siot.chatbot.answer").tag("status", status).timer();
        return timer == null ? 0 : timer.count();
    }

    // ---------------------------------------------------------------- CB-05

    @Test
    void personalNumbersAreMaskedBeforeAiAndStorage() throws Exception {
        Cookie session = signup();
        long conversationId = startConversation(session, "ko");
        when(queryEmbedder.embedQuery(anyString())).thenReturn(axis(AXIS_REGISTRATION));
        when(answerGenerator.generate(anyString(), anyString(), anyList(), anyList())).thenReturn("등록증 재발급 안내입니다.");

        ask(session, conversationId, "제 등록번호 900101-5123456, 전화 010-1234-5678 인데 재발급 되나요?")
                .andExpect(jsonPath("$.data.masked").value(true))
                .andExpect(jsonPath("$.data.question.content").value("제 등록번호 [등록번호], 전화 [전화번호] 인데 재발급 되나요?"));

        String masked = "제 등록번호 [등록번호], 전화 [전화번호] 인데 재발급 되나요?";
        verify(queryEmbedder).embedQuery(masked);
        verify(answerGenerator).generate(eq(masked), any(), anyList(), anyList());
        Integer leaked = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM chat_message WHERE content LIKE '%5123456%' OR content LIKE '%1234-5678%'",
                Integer.class);
        assertThat(leaked).isZero();
    }

    // ---------------------------------------------------------------- CB-07

    @Test
    void followUpQuestionIsRewrittenForSearchAndAnsweredWithHistory() throws Exception {
        Cookie session = signup();
        long conversationId = startConversation(session, "ko");
        when(queryEmbedder.embedQuery(anyString())).thenReturn(axis(AXIS_REGISTRATION));
        when(answerGenerator.generate(anyString(), anyString(), anyList(), anyList()))
                .thenReturn("입국 후 90일 이내에 해야 해요.", "네, 온라인으로도 신청할 수 있어요.");

        ask(session, conversationId, "외국인등록은 언제까지 해야 하나요?").andExpect(status().isOk());
        // 첫 질문은 다시 쓰지 않는다
        verify(queryRewriter, never()).rewrite(anyString(), anyList());

        when(queryRewriter.rewrite(eq("그거 온라인으로도 돼요?"), anyList()))
                .thenReturn("외국인등록은 온라인으로도 신청할 수 있나요?");
        ask(session, conversationId, "그거 온라인으로도 돼요?")
                .andExpect(jsonPath("$.data.answer.answerStatus").value("ANSWERED"))
                .andExpect(jsonPath("$.data.answer.content").value("네, 온라인으로도 신청할 수 있어요."))
                // 저장되는 질문은 사용자가 쓴 그대로
                .andExpect(jsonPath("$.data.question.content").value("그거 온라인으로도 돼요?"));

        List<ChatTurn> expectedHistory = List.of(new ChatTurn("외국인등록은 언제까지 해야 하나요?", "입국 후 90일 이내에 해야 해요."));
        verify(queryRewriter).rewrite("그거 온라인으로도 돼요?", expectedHistory);
        // 검색은 독립 질문으로, 답변은 원래 질문 + 앞 대화로
        verify(queryEmbedder).embedQuery("외국인등록은 온라인으로도 신청할 수 있나요?");
        verify(answerGenerator).generate(eq("그거 온라인으로도 돼요?"), eq("ko"), anyList(), eq(expectedHistory));
    }

    @Test
    void rewriteFailureFallsBackToPreviousQuestion() throws Exception {
        Cookie session = signup();
        long conversationId = startConversation(session, "ko");
        when(queryEmbedder.embedQuery(anyString())).thenReturn(axis(AXIS_UNRELATED));
        ask(session, conversationId, "자동차세는 언제 내요?").andExpect(status().isOk());

        when(queryRewriter.rewrite(anyString(), anyList())).thenThrow(new AiUnavailableException("timeout"));
        ask(session, conversationId, "카드로도 돼요?").andExpect(status().isOk());

        // 근거 없음 답변은 앞 대화의 답으로 넘기지 않는다
        verify(queryRewriter).rewrite("카드로도 돼요?", List.of(new ChatTurn("자동차세는 언제 내요?", null)));
        verify(queryEmbedder).embedQuery("자동차세는 언제 내요?\n카드로도 돼요?");
    }

    @Test
    void historyIsLimitedToRecentTurns() throws Exception {
        Cookie session = signup();
        long conversationId = startConversation(session, "ko");
        when(queryEmbedder.embedQuery(anyString())).thenReturn(axis(AXIS_UNRELATED));
        when(queryRewriter.rewrite(anyString(), anyList())).thenAnswer(inv -> inv.getArgument(0));
        for (int i = 1; i <= 4; i++) {
            ask(session, conversationId, "질문 " + i).andExpect(status().isOk());
        }
        ask(session, conversationId, "질문 5").andExpect(status().isOk());

        // 기본 3턴: 질문 2·3·4
        verify(queryRewriter).rewrite("질문 5", List.of(
                new ChatTurn("질문 2", null), new ChatTurn("질문 3", null), new ChatTurn("질문 4", null)));
    }

    // ---------------------------------------------------------------- CB-09

    @Test
    void emergencyQuestionShowsEmergencyContacts() throws Exception {
        Cookie session = signup();
        long conversationId = startConversation(session, "ko");
        when(queryEmbedder.embedQuery(anyString())).thenReturn(axis(AXIS_UNRELATED));

        ask(session, conversationId, "집에 불이 났어요 어떻게 해요?")
                .andExpect(jsonPath("$.data.answer.emergencyContacts.length()").value(2))
                .andExpect(jsonPath("$.data.answer.emergencyContacts[0].number").value("119"))
                .andExpect(jsonPath("$.data.answer.emergencyContacts[0].label").value("화재·구급"))
                .andExpect(jsonPath("$.data.answer.emergencyContacts[1].number").value("112"))
                // 질문 메시지에는 붙이지 않는다
                .andExpect(jsonPath("$.data.question.emergencyContacts.length()").value(0));

        // 대화 기록에서도 다시 보인다
        mockMvc.perform(get("/api/chat/conversations/{id}/messages", conversationId).cookie(session))
                .andExpect(jsonPath("$.data[1].emergencyContacts[0].number").value("119"));
    }

    // ---------------------------------------------------------------- CB-15

    @Test
    void answersInQuestionLanguageEvenIfConversationLanguageDiffers() throws Exception {
        Cookie session = signup();
        long conversationId = startConversation(session, "ko");
        when(queryEmbedder.embedQuery(anyString())).thenReturn(axis(AXIS_UNRELATED));

        // 한국어 대화에서 베트남어로 질문 → 근거 없음 안내도 베트남어
        ask(session, conversationId, "Thuế ô tô nộp khi nào?")
                .andExpect(jsonPath("$.data.answer.answerStatus").value("NO_EVIDENCE"))
                .andExpect(jsonPath("$.data.answer.content")
                        .value(org.hamcrest.Matchers.startsWith("Tôi không tìm thấy")));

        // 영어 응급 질문 → 연락처 라벨도 영어, 대화 기록에서도 영어
        ask(session, conversationId, "There is a fire in my building")
                .andExpect(jsonPath("$.data.answer.emergencyContacts[0].label").value("Fire & Ambulance"));
        mockMvc.perform(get("/api/chat/conversations/{id}/messages", conversationId).cookie(session))
                .andExpect(jsonPath("$.data[1].content").value(org.hamcrest.Matchers.startsWith("Tôi không tìm thấy")))
                .andExpect(jsonPath("$.data[3].emergencyContacts[0].label").value("Fire & Ambulance"));

        // 판별하지 못하면 대화 언어 (한국어)
        ask(session, conversationId, "CL-01")
                .andExpect(jsonPath("$.data.answer.content")
                        .value(org.hamcrest.Matchers.startsWith("자료에서 찾지 못했어요")));
    }

    // ---------------------------------------------------------------- CB-11

    @Test
    void judgmentQuestionShowsReferral() throws Exception {
        Cookie session = signup();
        long conversationId = startConversation(session, "ko");
        when(queryEmbedder.embedQuery(anyString())).thenReturn(axis(AXIS_UNRELATED));

        ask(session, conversationId, "제 상황이면 비자 연장 가능할까요? 처벌받나요?")
                .andExpect(jsonPath("$.data.answer.referral.notice").isNotEmpty())
                .andExpect(jsonPath("$.data.answer.referral.contacts.length()").value(2))
                .andExpect(jsonPath("$.data.answer.referral.contacts[0].topic").value("VISA"))
                .andExpect(jsonPath("$.data.answer.referral.contacts[0].number").value("1345"))
                .andExpect(jsonPath("$.data.answer.referral.contacts[1].topic").value("LEGAL"))
                .andExpect(jsonPath("$.data.answer.referral.contacts[1].number").value("132"))
                .andExpect(jsonPath("$.data.question.referral").doesNotExist());

        // 대화 기록에서도 다시 보인다
        mockMvc.perform(get("/api/chat/conversations/{id}/messages", conversationId).cookie(session))
                .andExpect(jsonPath("$.data[1].referral.contacts[0].number").value("1345"));

        // 일반 질문에는 붙지 않는다
        ask(session, conversationId, "외국인등록은 어떻게 하나요?")
                .andExpect(jsonPath("$.data.answer.referral").doesNotExist());
    }

    // ---------------------------------------------------------------- CB-04

    @Test
    void answerCanBeRatedByOwnerOnly() throws Exception {
        Cookie session = signup();
        long conversationId = startConversation(session, "ko");
        when(queryEmbedder.embedQuery(anyString())).thenReturn(axis(AXIS_UNRELATED));
        String body = ask(session, conversationId, "도서관은 몇 시까지 해요?")
                .andReturn().getResponse().getContentAsString();
        long questionId = Long.parseLong(com.jayway.jsonpath.JsonPath.read(body, "$.data.question.messageId").toString());
        long answerId = Long.parseLong(com.jayway.jsonpath.JsonPath.read(body, "$.data.answer.messageId").toString());

        feedback(session, conversationId, answerId, false, "전화 010-1234-5678 로 알려 주세요")
                .andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT feedback_comment FROM chat_message WHERE message_id = ?", String.class, answerId))
                .isEqualTo("전화 [전화번호] 로 알려 주세요");
        mockMvc.perform(get("/api/chat/conversations/{id}/messages", conversationId).cookie(session))
                .andExpect(jsonPath("$.data[1].helpful").value(false));

        // 다시 평가하면 덮어쓴다
        feedback(session, conversationId, answerId, true, null).andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT is_helpful FROM chat_message WHERE message_id = ?", Boolean.class, answerId)).isTrue();

        // 질문은 평가할 수 없다
        feedback(session, conversationId, questionId, true, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CHAT_FEEDBACK_NOT_ALLOWED"));
        // 다른 대화의 메시지 번호
        long otherConversation = startConversation(session, "ko");
        feedback(session, otherConversation, answerId, true, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT_MESSAGE_NOT_FOUND"));
        // 다른 회원
        feedback(signup(), conversationId, answerId, true, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT_CONVERSATION_NOT_FOUND"));
    }

    // ---------------------------------------------------------------- CB-08

    @Test
    void suggestionsDependOnUserTypeAndLanguage() throws Exception {
        Cookie session = signup();
        String email = com.jayway.jsonpath.JsonPath.read(mockMvc.perform(get("/api/members/me").cookie(session))
                .andReturn().getResponse().getContentAsString(), "$.data.email");

        // 온보딩 전: 공통 질문, 회원 언어(ko)
        mockMvc.perform(get("/api/chat/suggestions").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userType").doesNotExist())
                .andExpect(jsonPath("$.data.language").value("ko"))
                .andExpect(jsonPath("$.data.questions.length()").value(3))
                .andExpect(jsonPath("$.data.questions[0]").value("외국인등록은 어떻게 하나요?"));

        jdbcTemplate.update("UPDATE app_user SET user_type = 'NEW' WHERE email = ?", email);
        mockMvc.perform(get("/api/chat/suggestions").param("language", "en").cookie(session))
                .andExpect(jsonPath("$.data.userType").value("NEW"))
                .andExpect(jsonPath("$.data.questions[1]").value("How do I report a change of address after moving?"));

        jdbcTemplate.update("UPDATE app_user SET user_type = 'LIVING' WHERE email = ?", email);
        mockMvc.perform(get("/api/chat/suggestions").param("language", "zh").cookie(session))
                .andExpect(jsonPath("$.data.userType").value("LIVING"))
                .andExpect(jsonPath("$.data.questions[0]").value("居民税什么时候交？"));

        mockMvc.perform(get("/api/chat/suggestions").param("language", "fr").cookie(session))
                .andExpect(status().isBadRequest());
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
        // CB-10: 항목이 근거로 삼는 문서 쪽
        insertGuideReference("CL-01", current, 10, 14);
        insertGuideReference("CL-10", current, 30, 30);
        insertGuideReference("CL-05", current, 50, 60);

        // 현재 버전으로 지정됐지만 아직 반영 중인 문서는 검색되지 않아야 한다
        Long processing = jdbcTemplate.queryForObject(
                "INSERT INTO document (name) VALUES ('반영 중인 문서') RETURNING document_id", Long.class);
        Long processingVersion = jdbcTemplate.queryForObject(
                "INSERT INTO document_version (document_id, version_no, file_storage_key, processing_status, is_current) "
                        + "VALUES (?, 1, 'test/p.pdf', 'PROCESSING', true) RETURNING document_version_id",
                Long.class, processing);
        insertChunk(processingVersion, 1, 1, 1, "반영 중인 문서의 외국인등록 안내", AXIS_REGISTRATION);

        Long inactive = jdbcTemplate.queryForObject("""
                INSERT INTO document (name, is_active) VALUES ('사용 중지된 문서', false) RETURNING document_id""", Long.class);
        insertChunk(insertVersion(inactive, 1, true), 1, 1, 1, "사용 중지된 문서의 외국인등록 안내", AXIS_REGISTRATION);
        seeded = true;
    }

    private void insertGuideReference(String code, Long versionId, int pageStart, int pageEnd) {
        jdbcTemplate.update("INSERT INTO guide_document_reference (guide_id, document_version_id, page_start, page_end) "
                + "SELECT guide_id, ?, ?, ? FROM guide WHERE code = ?", versionId, pageStart, pageEnd, code);
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

    private ResultActions feedback(Cookie session, long conversationId, long messageId, boolean helpful,
                                   String comment) throws Exception {
        String json = comment == null
                ? "{\"helpful\":" + helpful + "}"
                : "{\"helpful\":" + helpful + ",\"comment\":\"" + comment + "\"}";
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/chat/conversations/{cid}/messages/{mid}/feedback", conversationId, messageId)
                .cookie(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions ask(Cookie session, long conversationId, String question) throws Exception {
        return mockMvc.perform(post("/api/chat/conversations/{id}/messages", conversationId)
                .cookie(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"" + question.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}"));
    }
}
