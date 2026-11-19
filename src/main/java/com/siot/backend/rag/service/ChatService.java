package com.siot.backend.rag.service;

import com.siot.backend.global.exception.BusinessException;
import com.siot.backend.global.exception.ErrorCode;
import com.siot.backend.member.domain.AppUser;
import com.siot.backend.member.repository.AppUserRepository;
import com.siot.backend.rag.ai.AiUnavailableException;
import com.siot.backend.rag.ai.AnswerGenerator;
import com.siot.backend.rag.ai.ChatTurn;
import com.siot.backend.rag.ai.QueryRewriter;
import com.siot.backend.rag.ai.QueryEmbedder;
import com.siot.backend.rag.ai.RetrievedChunk;
import com.siot.backend.rag.config.ChatbotProperties;
import com.siot.backend.rag.domain.AnswerStatus;
import com.siot.backend.rag.domain.ChatConversation;
import com.siot.backend.rag.domain.ChatMessage;
import com.siot.backend.rag.domain.ChatMessageRelatedPage;
import com.siot.backend.rag.domain.ChatMessageSource;
import com.siot.backend.rag.domain.DocumentChunk;
import com.siot.backend.rag.domain.ReferralTopic;
import com.siot.backend.rag.dto.AskResponse;
import com.siot.backend.rag.dto.ConversationResponse;
import com.siot.backend.rag.dto.EmergencyContact;
import com.siot.backend.rag.dto.Referral;
import com.siot.backend.rag.dto.RelatedPage;
import com.siot.backend.rag.dto.SuggestionsResponse;
import com.siot.backend.rag.dto.MessageResponse;
import com.siot.backend.rag.dto.SourceResponse;
import com.siot.backend.rag.repository.ChatConversationRepository;
import com.siot.backend.rag.repository.ChatMessageRelatedPageRepository;
import com.siot.backend.rag.repository.ChatMessageRepository;
import com.siot.backend.rag.repository.ChatMessageSourceRepository;
import com.siot.backend.rag.repository.DocumentChunkRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 챗봇 대화 (CB-03, CB-16) 와 질문 처리 파이프라인.
 * <ol>
 *   <li>개인정보 가리기 (CB-05) → 질문 저장</li>
 *   <li>앞 대화가 있으면 질문을 검색용 독립 질문으로 바꾸기 (CB-07)</li>
 *   <li>질문 임베딩 → 관련 문서 조각 검색 (CB-14)</li>
 *   <li>가장 관련 높은 조각이 기준보다 낮으면 답변하지 않고 문의처 안내 (CB-12)</li>
 *   <li>검색된 조각만 근거로 Gemini 답변 생성 (CB-13). 앞 대화도 함께 넘긴다 (CB-07).
 *       모델이 근거에 답이 없다고 하면 근거 없음과 같이 처리</li>
 *   <li>답변과 근거(출처) 저장 (CB-17)</li>
 * </ol>
 * Gemini 호출 중에는 DB 트랜잭션을 잡지 않는다 (질문 저장 / 답변 저장을 각각 짧은 트랜잭션으로).
 */
@Slf4j
@Service
public class ChatService {

    private static final java.util.Set<String> SUPPORTED_LANGUAGES = java.util.Set.of("ko", "en", "zh", "vi");

    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatMessageSourceRepository sourceRepository;
    private final ChatMessageRelatedPageRepository relatedPageRepository;
    private final DocumentChunkRepository chunkRepository;
    private final AppUserRepository appUserRepository;
    private final QueryEmbedder queryEmbedder;
    private final AnswerGenerator answerGenerator;
    private final QueryRewriter queryRewriter;
    private final ChunkRetriever chunkRetriever;
    private final RelatedPageFinder relatedPageFinder;
    private final EventRelatedPageFinder eventRelatedPageFinder;
    private final ChatbotProperties properties;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public ChatService(ChatConversationRepository conversationRepository,
                       ChatMessageRepository messageRepository,
                       ChatMessageSourceRepository sourceRepository,
                       ChatMessageRelatedPageRepository relatedPageRepository,
                       DocumentChunkRepository chunkRepository,
                       AppUserRepository appUserRepository,
                       QueryEmbedder queryEmbedder,
                       AnswerGenerator answerGenerator,
                       QueryRewriter queryRewriter,
                       ChunkRetriever chunkRetriever,
                       RelatedPageFinder relatedPageFinder,
                       EventRelatedPageFinder eventRelatedPageFinder,
                       ChatbotProperties properties,
                       PlatformTransactionManager transactionManager,
                       Clock clock,
                       MeterRegistry meterRegistry) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.sourceRepository = sourceRepository;
        this.relatedPageRepository = relatedPageRepository;
        this.chunkRepository = chunkRepository;
        this.appUserRepository = appUserRepository;
        this.queryEmbedder = queryEmbedder;
        this.answerGenerator = answerGenerator;
        this.queryRewriter = queryRewriter;
        this.chunkRetriever = chunkRetriever;
        this.relatedPageFinder = relatedPageFinder;
        this.eventRelatedPageFinder = eventRelatedPageFinder;
        this.properties = properties;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.meterRegistry = meterRegistry;
    }

    /** CB-02: 답변 생성 시간 (목표 평균 5초 이하). /actuator/metrics/siot.chatbot.answer (관리자) */
    static final String ANSWER_TIMER = "siot.chatbot.answer";

    // ------------------------------------------------------------------ 대화 (CB-03)

    /** 새 대화 ('새 대화' 버튼) */
    @Transactional
    public ConversationResponse startConversation(Long userId, String language) {
        String lang = language != null ? language : appUserRepository.findById(userId)
                .map(user -> user.getLanguage())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        ChatConversation conversation = conversationRepository.save(ChatConversation.start(userId, lang));
        return toResponse(conversation, null);
    }

    /**
     * 대화 삭제 (CB-03 "지울 수 있다"). 질문·답변·출처는 DB 에서 함께 지워지고(ON DELETE CASCADE) 복구할 수 없다.
     * 관리자 검토 목록(ADM-08)에서도 빠진다.
     */
    @Transactional
    public void deleteConversation(Long userId, Long conversationId) {
        conversationRepository.delete(findOwned(userId, conversationId));
    }

    /** 이전 대화 목록 (최근 대화 순) */
    @Transactional(readOnly = true)
    public Page<ConversationResponse> listConversations(Long userId, Pageable pageable) {
        Page<ChatConversation> page = conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId, pageable);
        // 제목(첫 질문)은 페이지의 대화들을 한 번에 조회한다
        Map<Long, String> titles = page.isEmpty() ? Map.of() : messageRepository
                .findFirstQuestions(page.getContent().stream().map(ChatConversation::getId).toList()).stream()
                .collect(Collectors.toMap(m -> m.getConversation().getId(), ChatMessage::getContent));
        return page.map(c -> toResponse(c, titles.get(c.getId())));
    }

    /** 대화 내용 (근거 포함) */
    @Transactional(readOnly = true)
    public List<MessageResponse> getMessages(Long userId, Long conversationId) {
        ChatConversation conversation = findOwned(userId, conversationId);
        String language = conversation.getLanguage();
        List<ChatMessage> messages = messageRepository.findByConversationIdOrderByIdAsc(conversation.getId());
        List<Long> messageIds = messages.stream().map(ChatMessage::getId).toList();
        Map<Long, List<ChatMessageSource>> sources = loadSources(messageIds);
        Map<Long, List<RelatedPage>> savedPages = loadRelatedPages(messageIds);
        List<MessageResponse> responses = new ArrayList<>();
        String lastQuestion = null;
        for (ChatMessage m : messages) {
            if (!m.isAnswer()) {
                lastQuestion = m.getContent();
                responses.add(toResponse(m, List.of(), List.of(), language));
                continue;
            }
            List<ChatMessageSource> messageSources = sources.getOrDefault(m.getId(), List.of());
            // CB-15: 답변은 답한 언어로 다시 보여 준다 (연락처 라벨·담당 기관 안내·관련 페이지 제목)
            String messageLanguage = m.getLanguage() != null ? m.getLanguage() : language;
            // CB-10: 답변할 때 보여 준 링크를 그대로. 이 기능 이전 답변만 근거 조각·질문으로 다시 계산한다
            List<RelatedPage> pages;
            if (m.isRelatedPagesRecorded()) {
                pages = savedPages.getOrDefault(m.getId(), List.of());
            } else {
                List<Long> chunkIds = messageSources.stream()
                        .filter(s -> s.getChunk() != null).map(s -> s.getChunk().getId()).toList();
                pages = relatedPages(lastQuestion, chunkIds, messageLanguage);
            }
            responses.add(toResponse(m, messageSources.stream().map(ChatService::toResponse).toList(),
                    pages, messageLanguage));
        }
        return responses;
    }

    // ------------------------------------------------------------------ 추천 질문 (CB-08)

    @Transactional(readOnly = true)
    public SuggestionsResponse suggestions(Long userId, String language) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (language != null && !SUPPORTED_LANGUAGES.contains(language)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        String lang = language != null ? language : user.getLanguage();
        return new SuggestionsResponse(
                user.getUserType() == null ? null : user.getUserType().name(),
                lang,
                ChatSuggestions.forUser(user.getUserType(), lang));
    }

    // ------------------------------------------------------------------ 답변 평가 (CB-04)

    @Transactional
    public void recordFeedback(Long userId, Long conversationId, Long messageId, boolean helpful, String comment) {
        ChatConversation conversation = findOwned(userId, conversationId);
        ChatMessage message = messageRepository.findById(messageId)
                .filter(m -> m.getConversation().getId().equals(conversation.getId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_MESSAGE_NOT_FOUND));
        if (!message.isAnswer()) {
            throw new BusinessException(ErrorCode.CHAT_FEEDBACK_NOT_ALLOWED);
        }
        String trimmed = comment == null || comment.isBlank() ? null : comment.strip();
        // CB-05: 의견도 개인정보를 가려서 저장 (관리자 화면에는 가린 값만 보인다)
        message.recordFeedback(helpful, trimmed == null ? null : PiiMasker.mask(trimmed).text());
    }

    // ------------------------------------------------------------------ 질문 (CB-16)

    public AskResponse ask(Long userId, Long conversationId, String rawQuestion) {
        String trimmed = rawQuestion == null ? "" : rawQuestion.strip();
        if (trimmed.isEmpty() || trimmed.length() > properties.maxQuestionLength()) {
            throw new BusinessException(ErrorCode.CHAT_QUESTION_LENGTH);
        }

        // 1. 개인정보를 가린 질문만 저장하고 AI 로 보낸다 (CB-05)
        PiiMasker.Result masked = PiiMasker.mask(trimmed);
        String question = masked.text();
        record Saved(ChatConversation conversation, ChatMessage question, List<ChatTurn> history) {
        }
        Saved saved = tx.execute(status -> {
            ChatConversation conversation = findOwned(userId, conversationId);
            List<ChatTurn> history = recentTurns(conversation.getId());
            ChatMessage message = messageRepository.save(ChatMessage.question(conversation, question));
            conversation.touch(OffsetDateTime.now(clock));
            return new Saved(conversation, message, history);
        });
        // CB-15: 질문 언어로 답한다. 판별하지 못하면 화면(대화) 언어
        //   (원문으로 판별: 가린 자리의 "[전화번호]" 같은 한국어 표시에 끌려가지 않도록)
        String language = QuestionLanguageDetector.detect(trimmed).orElse(saved.conversation().getLanguage());

        // 2~4. 검색 → 근거 판단 → 답변 생성 (트랜잭션 밖). CB-02: 걸린 시간을 상태별로 기록
        Timer.Sample sample = Timer.start(meterRegistry);
        Outcome outcome = answer(question, language, saved.history());
        long nanos = sample.stop(Timer.builder(ANSWER_TIMER)
                .description("챗봇 답변 생성 시간 (검색·답변 생성, CB-02 목표 평균 5초 이하)")
                .tag("status", outcome.status().name())
                .register(meterRegistry));
        log.info("Chatbot answered: status={} took={}ms", outcome.status(), nanos / 1_000_000);
        // CB-09: 응급 관련 질문이면 답변과 함께 119/112 를 보여 준다
        boolean emergency = EmergencyDetector.isEmergency(question);
        // CB-11: 비자 발급 가능 여부·법률·의료 판단 질문이면 고정 안내와 담당 기관을 함께 보여 준다
        List<ReferralTopic> referralTopics = ReferralDetector.detect(question);

        // CB-10: 관련 페이지는 답변과 함께 저장해 대화 기록에서 같은 링크를 보여 준다
        List<RelatedPage> relatedPages = relatedPages(question, outcome.relatedChunkIds(), language);

        // 5. 답변·근거·관련 페이지 저장
        ChatMessage answer = tx.execute(status -> {
            // 답변을 만드는 동안 대화가 지워졌으면 (CB-03 삭제·탈퇴) FK 오류(500) 대신 404
            ChatConversation conversation = conversationRepository.findById(saved.conversation().getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_CONVERSATION_NOT_FOUND));
            ChatMessage message = messageRepository.save(
                    ChatMessage.answer(conversation, outcome.text(), outcome.status(), emergency, referralTopics,
                            language));
            // 검색 뒤 문서 재반영(ADM-04)으로 지워진 조각은 연결하지 않는다 (삭제 시 SET NULL 과 같은 상태로 저장)
            Set<Long> existingChunkIds = outcome.sources().isEmpty() ? Set.of() : new HashSet<>(
                    chunkRepository.findExistingIds(outcome.sources().stream().map(RetrievedChunk::chunkId).toList()));
            List<ChatMessageSource> sources = new ArrayList<>();
            for (int i = 0; i < outcome.sources().size(); i++) {
                RetrievedChunk chunk = outcome.sources().get(i);
                DocumentChunk chunkRef = existingChunkIds.contains(chunk.chunkId())
                        ? chunkRepository.getReferenceById(chunk.chunkId()) : null;
                sources.add(ChatMessageSource.of(message, chunkRef, chunk.relevance(), i + 1));
            }
            sourceRepository.saveAll(sources);
            List<ChatMessageRelatedPage> pages = new ArrayList<>();
            for (int i = 0; i < relatedPages.size(); i++) {
                pages.add(ChatMessageRelatedPage.of(message.getId(), i + 1, relatedPages.get(i)));
            }
            relatedPageRepository.saveAll(pages);
            return message;
        });

        List<SourceResponse> sourceResponses = new ArrayList<>();
        for (int i = 0; i < outcome.sources().size(); i++) {
            RetrievedChunk c = outcome.sources().get(i);
            sourceResponses.add(new SourceResponse(i + 1, c.documentName(), c.pageStart(), c.pageEnd(),
                    c.sourceUrl(), c.relevance()));
        }
        return new AskResponse(
                toResponse(saved.question(), List.of(), List.of(), language),
                toResponse(answer, sourceResponses, relatedPages, language),
                masked.masked());
    }

    /**
     * @param sources         답변 근거 (ANSWERED 만, 저장·출처 표시)
     * @param relatedChunkIds 관련 페이지를 찾을 조각. ANSWERED 는 근거, ERROR 는 답변 생성 전에 검색해 둔 조각 (CB-02)
     */
    private record Outcome(String text, AnswerStatus status, List<RetrievedChunk> sources, List<Long> relatedChunkIds) {

        static Outcome of(String text, AnswerStatus status, List<RetrievedChunk> sources) {
            return new Outcome(text, status, sources, sources.stream().map(RetrievedChunk::chunkId).toList());
        }
    }

    private Outcome answer(String question, String language, List<ChatTurn> history) {
        List<RetrievedChunk> relevant = List.of();
        try {
            String searchQuery = searchQuery(question, history);
            float[] vector = queryEmbedder.embedQuery(searchQuery);
            List<RetrievedChunk> chunks = chunkRetriever.search(vector, properties.topK());
            // CB-12: 관련도가 기준 미만인 조각은 근거로 쓰지 않는다
            relevant = chunks.stream()
                    .filter(c -> c.relevance() >= properties.minRelevance())
                    .toList();
            if (relevant.isEmpty()) {
                log.info("Chatbot no evidence: best relevance={}",
                        chunks.isEmpty() ? "none" : String.format("%.3f", chunks.get(0).relevance()));
                return Outcome.of(ChatbotMessages.noEvidence(language), AnswerStatus.NO_EVIDENCE, List.of());
            }

            // CB-17: 답변 근거로 넘긴 조각과 화면에 보여 주는 출처를 같게 한다
            //   (넘긴 조각이 더 많으면 출처에 없는 자료의 내용이 답변에 들어갈 수 있다)
            List<RetrievedChunk> sources = relevant.stream().limit(properties.maxSources()).toList();
            String text = answerGenerator.generate(question, language, sources, history);
            if (text == null || text.isBlank() || text.strip().startsWith(AnswerGenerator.NO_ANSWER)) {
                return Outcome.of(ChatbotMessages.noEvidence(language), AnswerStatus.NO_EVIDENCE, List.of());
            }
            return Outcome.of(text, AnswerStatus.ANSWERED, sources);
        } catch (AiUnavailableException e) {
            log.warn("Chatbot AI unavailable: {}", e.getMessage());
            // CB-02: 출처는 보이지 않지만, 검색까지 됐으면 그 조각으로 관련 페이지 링크를 보여 준다
            return new Outcome(ChatbotMessages.unavailable(language), AnswerStatus.ERROR, List.of(),
                    relevant.stream().limit(properties.maxSources()).map(RetrievedChunk::chunkId).toList());
        }
    }

    /**
     * CB-10·CB-02: 근거 조각과 연결된 항목, 없으면 질문에 제목이 들어 있는 항목.
     * (생활수칙처럼 문서 연결이 없는 항목, AI 오류로 검색하지 못한 경우도 링크를 보여 주기 위해)
     * 행사를 묻는 질문이면 다가오는 행사를 앞에 둔다. 항목 링크가 있으면 행사는 2개까지만 (항목 1개 자리 남김).
     */
    private List<RelatedPage> relatedPages(String question, List<Long> chunkIds, String language) {
        List<RelatedPage> guides = relatedPageFinder.find(chunkIds, language);
        if (guides.isEmpty()) {
            guides = relatedPageFinder.findByTitle(question, language);
        }
        int max = RelatedPageFinder.MAX_PAGES;
        List<RelatedPage> events = eventRelatedPageFinder.find(question, language, guides.isEmpty() ? max : max - 1);
        if (events.isEmpty()) {
            return guides;
        }
        List<RelatedPage> pages = new ArrayList<>(events);
        guides.stream().limit(max - events.size()).forEach(pages::add);
        return pages;
    }

    /**
     * CB-07: 앞 대화가 있으면 검색용 독립 질문으로 바꾼다.
     * 바꾸기에 실패하면 직전 질문을 앞에 붙여 검색한다 (주제를 놓치지 않도록).
     */
    private String searchQuery(String question, List<ChatTurn> history) {
        if (history.isEmpty()) {
            return question;
        }
        try {
            String rewritten = queryRewriter.rewrite(question, history);
            if (rewritten != null && !rewritten.isBlank()) {
                // 모델이 개인정보를 되살려 쓰는 일이 없도록 한 번 더 가린다
                return PiiMasker.mask(rewritten.strip()).text();
            }
        } catch (AiUnavailableException e) {
            log.warn("Chatbot query rewrite failed, falling back to previous question: {}", e.getMessage());
        }
        return history.get(history.size() - 1).question() + "\n" + question;
    }

    /** 최근 N턴 (오래된 것부터). 답변은 근거로 답한 경우만 넣는다 */
    private List<ChatTurn> recentTurns(Long conversationId) {
        if (properties.historyTurns() == 0) {
            return List.of();
        }
        List<ChatMessage> messages = messageRepository.findByConversationIdOrderByIdAsc(conversationId);
        List<ChatTurn> turns = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (!ChatMessage.ROLE_USER.equals(message.getRole())) {
                continue;
            }
            ChatMessage next = i + 1 < messages.size() ? messages.get(i + 1) : null;
            String answer = next != null && ChatMessage.ROLE_ASSISTANT.equals(next.getRole())
                    && AnswerStatus.ANSWERED.name().equals(next.getAnswerStatus()) ? next.getContent() : null;
            turns.add(new ChatTurn(message.getContent(), answer));
        }
        int from = Math.max(0, turns.size() - properties.historyTurns());
        return List.copyOf(turns.subList(from, turns.size()));
    }

    // ------------------------------------------------------------------ helpers

    private ChatConversation findOwned(Long userId, Long conversationId) {
        // NFR-03: 다른 회원의 대화는 존재 여부도 드러내지 않는다
        return conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_CONVERSATION_NOT_FOUND));
    }

    private Map<Long, List<ChatMessageSource>> loadSources(List<Long> messageIds) {
        if (messageIds.isEmpty()) {
            return Map.of();
        }
        return sourceRepository.findByMessageIdInOrderBySourceOrderAsc(messageIds).stream()
                .collect(Collectors.groupingBy(s -> s.getMessage().getId()));
    }

    private Map<Long, List<RelatedPage>> loadRelatedPages(List<Long> messageIds) {
        if (messageIds.isEmpty()) {
            return Map.of();
        }
        return relatedPageRepository.findByMessageIdInOrderByMessageIdAscSortOrderAsc(messageIds).stream()
                .collect(Collectors.groupingBy(ChatMessageRelatedPage::getMessageId,
                        Collectors.mapping(ChatMessageRelatedPage::toRelatedPage, Collectors.toList())));
    }

    private static SourceResponse toResponse(ChatMessageSource source) {
        DocumentChunk chunk = source.getChunk();
        if (chunk == null) {
            return new SourceResponse(source.getSourceOrder(), null, null, null, null, source.getRelevanceScore());
        }
        var document = chunk.getDocumentVersion().getDocument();
        return new SourceResponse(source.getSourceOrder(), document.getName(), chunk.getPageStart(),
                chunk.getPageEnd(), document.getSourceUrl(), source.getRelevanceScore());
    }

    private static MessageResponse toResponse(ChatMessage message, List<SourceResponse> sources,
                                              List<RelatedPage> relatedPages, String language) {
        return new MessageResponse(message.getId(), message.getRole(), message.getContent(),
                message.getAnswerStatus(), sources, relatedPages,
                message.isEmergency() ? EmergencyContact.forLanguage(language) : List.of(),
                Referral.of(message.getReferralTopicList(), language),
                message.getHelpful(), message.getCreatedAt());
    }

    private static ConversationResponse toResponse(ChatConversation conversation, String title) {
        return new ConversationResponse(conversation.getId(), conversation.getLanguage(), title,
                conversation.getCreatedAt(), conversation.getUpdatedAt());
    }
}
