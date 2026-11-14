package com.siot.backend.rag.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * ADM-08: 관리자 검토 대상 답변. 질문·답변·의견은 개인정보를 가린 값이며 회원 신원은 넣지 않는다.
 *
 * @param reason NEGATIVE (도움이 안 됐어요) / NO_EVIDENCE (근거를 찾지 못한 질문). 둘 다면 NEGATIVE
 */
public record ChatReviewResponse(
        Long messageId,
        Long conversationId,
        String language,
        String reason,
        String question,
        String answer,
        String answerStatus,
        Boolean helpful,
        String feedbackComment,
        List<SourceResponse> sources,
        boolean reviewed,
        String adminMemo,
        Long reviewedBy,
        OffsetDateTime reviewedAt,
        OffsetDateTime createdAt
) {
}
