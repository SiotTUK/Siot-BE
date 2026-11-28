package com.siot.backend.rag.service;

import com.siot.backend.global.exception.BusinessException;
import com.siot.backend.global.exception.ErrorCode;
import com.siot.backend.rag.domain.AnswerStatus;
import com.siot.backend.rag.domain.ChatMessage;
import com.siot.backend.rag.domain.ChatMessageSource;
import com.siot.backend.rag.domain.DocumentChunk;
import com.siot.backend.rag.dto.ChatReviewResponse;
import com.siot.backend.rag.dto.SourceResponse;
import com.siot.backend.rag.repository.ChatMessageRepository;
import com.siot.backend.rag.repository.ChatMessageSourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ADM-08: '도움이 안 됐어요' 답변(CB-04)과 근거를 찾지 못한 질문(CB-12)을 관리자가 확인하고
 * 처리 여부·메모를 남긴다 (자료 보충에 활용).
 */
@Service
@RequiredArgsConstructor
public class ChatReviewService {

    static final int KEYWORD_MAX = 100;

    public enum ReviewType { ALL, NEGATIVE, NO_EVIDENCE }

    private final ChatMessageRepository messageRepository;
    private final ChatMessageSourceRepository sourceRepository;
    private final Clock clock;

    /**
     * @param reviewed null 이면 처리 여부와 관계없이
     */
    @Transactional(readOnly = true)
    /**
     * @param messageId 답변 번호로 찾기 (null = 전체)
     * @param keyword   답변·질문·사용자 의견 부분 일치, 대소문자 무시 (비우면 전체). 개인정보를 가린 문구에서 찾는다
     */
    public Page<ChatReviewResponse> list(ReviewType type, Boolean reviewed, Long messageId, String keyword,
                                         Pageable pageable) {
        String search = keyword == null ? "" : keyword.strip().toLowerCase(java.util.Locale.ROOT);
        if (search.length() > KEYWORD_MAX) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "검색어는 " + KEYWORD_MAX + "자 이하로 입력해 주세요.");
        }
        boolean negative = type != ReviewType.NO_EVIDENCE;
        boolean noEvidence = type != ReviewType.NEGATIVE;
        Page<ChatMessage> page = messageRepository.findReviewTargets(
                negative, noEvidence, AnswerStatus.NO_EVIDENCE.name(), reviewed, messageId, search, pageable);
        Map<Long, List<SourceResponse>> sources = sourceRepository
                .findByMessageIdInOrderBySourceOrderAsc(page.getContent().stream().map(ChatMessage::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(s -> s.getMessage().getId(),
                        Collectors.mapping(ChatReviewService::toSource, Collectors.toList())));
        return page.map(m -> toResponse(m, sources.getOrDefault(m.getId(), List.of())));
    }

    @Transactional
    public ChatReviewResponse update(Long adminId, Long messageId, boolean reviewed, String adminMemo) {
        ChatMessage message = messageRepository.findById(messageId)
                .filter(ChatReviewService::isReviewTarget)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_REVIEW_NOT_FOUND));
        String memo = adminMemo == null || adminMemo.isBlank() ? null : adminMemo.strip();
        message.review(reviewed, memo, adminId, OffsetDateTime.now(clock));
        List<SourceResponse> sources = sourceRepository.findByMessageIdInOrderBySourceOrderAsc(List.of(messageId))
                .stream().map(ChatReviewService::toSource).toList();
        return toResponse(message, sources);
    }

    private static boolean isReviewTarget(ChatMessage m) {
        return m.isAnswer() && (Boolean.FALSE.equals(m.getHelpful())
                || AnswerStatus.NO_EVIDENCE.name().equals(m.getAnswerStatus()));
    }

    private ChatReviewResponse toResponse(ChatMessage m, List<SourceResponse> sources) {
        String question = messageRepository
                .findFirstByConversationIdAndRoleAndIdLessThanOrderByIdDesc(
                        m.getConversation().getId(), ChatMessage.ROLE_USER, m.getId())
                .map(ChatMessage::getContent)
                .orElse(null);
        String reason = Boolean.FALSE.equals(m.getHelpful()) ? "NEGATIVE" : "NO_EVIDENCE";
        return new ChatReviewResponse(m.getId(), m.getConversation().getId(), m.getConversation().getLanguage(),
                reason, question, m.getContent(), m.getAnswerStatus(), m.getHelpful(), m.getFeedbackComment(),
                sources, m.isReviewed(), m.getAdminMemo(), m.getReviewedBy(), m.getReviewedAt(), m.getCreatedAt());
    }

    private static SourceResponse toSource(ChatMessageSource source) {
        DocumentChunk chunk = source.getChunk();
        if (chunk == null) {
            return new SourceResponse(source.getSourceOrder(), null, null, null, null, source.getRelevanceScore());
        }
        var document = chunk.getDocumentVersion().getDocument();
        return new SourceResponse(source.getSourceOrder(), document.getName(), chunk.getPageStart(),
                chunk.getPageEnd(), document.getSourceUrl(), source.getRelevanceScore());
    }
}
