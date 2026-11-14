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
import com.siot.backend.rag.repository.ChatMessageRepository;
import com.siot.backend.rag.repository.ChatMessageSourceRepository;
import com.siot.backend.rag.repository.DocumentChunkRepository;
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
import java.util.List;
import java.util.Map;
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
    private final DocumentChunkRepository chunkRepository;
    private final AppUserRepository appUserRepository;
    private final QueryEmbedder queryEmbedder;
    private final AnswerGenerator answerGenerator;
    private final QueryRewriter queryRewriter;
    private final ChunkRetriever chunkRetriever;
    private final RelatedPageFinder relatedPageFinder;
    private final ChatbotProperties properties;
    private final TransactionTemplate tx;
    private final Clock clock;

    public ChatService(ChatConversationRepository conversationRepository,
                       ChatMessageRepository messageRepository,
                       ChatMessageSourceRepository sourceRepository,
                       DocumentChunkRepository chunkRepository,
                       AppUserRepository appUserRepository,
                       QueryEmbedder queryEmbedder,
                       AnswerGenerator answerGenerator,
                       QueryRewriter queryRewriter,
                       ChunkRetriever chunkRetriever,
                       RelatedPageFinder relatedPageFinder,
                       ChatbotProperties properties,
                       PlatformTransactionManager transactionManager,
                       Clock clock) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.sourceRepository = sourceRepository;
        this.chunkRepository = chunkRepository;
        this.appUserRepository = appUserRepository;
        this.queryEmbedder = queryEmbedder;
        this.answerGenerator = answerGenerator;
        this.queryRewriter = queryRewriter;
        this.chunkRetriever = chunkRetriever;
        this.relatedPageFinder = relatedPageFinder;
        this.properties = properties;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

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

    /** 이전 대화 목록 (최근 대화 순) */
    @Transactional(readOnly = true)
    public Page<ConversationResponse> listConversations(Long userId, Pageable pageable) {
        return conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId, pageable)
                .map(c -> toResponse(c, messageRepository
                        .findFirstByConversationIdAndRoleOrderByIdAsc(c.getId(), ChatMessage.ROLE_USER)
                        .map(ChatMessage::getContent)
                        .orElse(null)));
    }

    /** 대화 내용 (근거 포함) */
    @Transactional(readOnly = true)
    public List<MessageResponse> getMessages(Long userId, Long conversationId) {
        ChatConversation conversation = findOwned(userId, conversationId);
        String language = conversation.getLanguage();
        List<ChatMessage> messages = messageRepository.findByConversationIdOrderByIdAsc(conversation.getId());
        Map<Long, List<ChatMessageSource>> sources = loadSources(messages.stream().map(ChatMessage::getId).toList());
        return messages.stream().map(m -> {
            List<ChatMessageSource> messageSources = sources.getOrDefault(m.getId(), List.of());
            List<Long> chunkIds = messageSources.stream()
                    .filter(s -> s.getChunk() != null).map(s -> s.getChunk().getId()).toList();
            return toResponse(m, messageSources.stream().map(ChatService::toResponse).toList(),
                    relatedPageFinder.find(chunkIds, language), language);
        }).toList();
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
        String language = saved.conversation().getLanguage();

        // 2~4. 검색 → 근거 판단 → 답변 생성 (트랜잭션 밖)
        Outcome outcome = answer(question, language, saved.history());
        // CB-09: 응급 관련 질문이면 답변과 함께 119/112 를 보여 준다
        boolean emergency = EmergencyDetector.isEmergency(question);
        // CB-11: 비자 발급 가능 여부·법률·의료 판단 질문이면 고정 안내와 담당 기관을 함께 보여 준다
        List<ReferralTopic> referralTopics = ReferralDetector.detect(question);

        // 5. 답변·근거 저장
        ChatMessage answer = tx.execute(status -> {
            ChatConversation conversation = conversationRepository.getReferenceById(saved.conversation().getId());
            ChatMessage message = messageRepository.save(
                    ChatMessage.answer(conversation, outcome.text(), outcome.status(), emergency, referralTopics));
            List<ChatMessageSource> sources = new ArrayList<>();
            for (int i = 0; i < outcome.sources().size(); i++) {
                RetrievedChunk chunk = outcome.sources().get(i);
                DocumentChunk chunkRef = chunkRepository.getReferenceById(chunk.chunkId());
                sources.add(ChatMessageSource.of(message, chunkRef, chunk.relevance(), i + 1));
            }
            sourceRepository.saveAll(sources);
            return message;
        });

        List<SourceResponse> sourceResponses = new ArrayList<>();
        for (int i = 0; i < outcome.sources().size(); i++) {
            RetrievedChunk c = outcome.sources().get(i);
            sourceResponses.add(new SourceResponse(i + 1, c.documentName(), c.pageStart(), c.pageEnd(),
                    c.sourceUrl(), c.relevance()));
        }
        List<RelatedPage> relatedPages = relatedPageFinder.find(
                outcome.sources().stream().map(RetrievedChunk::chunkId).toList(), language);
        return new AskResponse(
                toResponse(saved.question(), List.of(), List.of(), language),
                toResponse(answer, sourceResponses, relatedPages, language),
                masked.masked());
    }

    private record Outcome(String text, AnswerStatus status, List<RetrievedChunk> sources) {
    }

    private Outcome answer(String question, String language, List<ChatTurn> history) {
        try {
            String searchQuery = searchQuery(question, history);
            float[] vector = queryEmbedder.embedQuery(searchQuery);
            List<RetrievedChunk> chunks = chunkRetriever.search(vector, properties.topK());
            // CB-12: 관련도가 기준 미만인 조각은 근거로 쓰지 않는다
            List<RetrievedChunk> relevant = chunks.stream()
                    .filter(c -> c.relevance() >= properties.minRelevance())
                    .toList();
            if (relevant.isEmpty()) {
                log.info("Chatbot no evidence: best relevance={}",
                        chunks.isEmpty() ? "none" : String.format("%.3f", chunks.get(0).relevance()));
                return new Outcome(ChatbotMessages.noEvidence(language), AnswerStatus.NO_EVIDENCE, List.of());
            }

            String text = answerGenerator.generate(question, language, relevant, history);
            if (text == null || text.isBlank() || text.strip().startsWith(AnswerGenerator.NO_ANSWER)) {
                return new Outcome(ChatbotMessages.noEvidence(language), AnswerStatus.NO_EVIDENCE, List.of());
            }
            List<RetrievedChunk> sources = relevant.stream().limit(properties.maxSources()).toList();
            return new Outcome(text, AnswerStatus.ANSWERED, sources);
        } catch (AiUnavailableException e) {
            log.warn("Chatbot AI unavailable: {}", e.getMessage());
            return new Outcome(ChatbotMessages.unavailable(language), AnswerStatus.ERROR, List.of());
        }
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
