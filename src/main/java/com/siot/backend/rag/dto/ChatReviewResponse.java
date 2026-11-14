package com.siot.backend.rag.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * ADM-08: 관리자 검토 대상 답변. 질문·답변·의견은 개인정보를 가린 값이며 회원 신원은 넣지 않는다.
 *
 * @param reason NEGATIVE (도움이 안 됐어요) / NO_EVIDENCE (근거를 찾지 못한 질문). 둘 다면 NEGATIVE
 */
@Schema(description = "ADM-08 검토 대상 답변 (개인정보는 가린 값, 회원 신원 없음)")
public record ChatReviewResponse(
        @Schema(description = "답변 메시지 ID", example = "32") Long messageId,
        @Schema(description = "대화 ID", example = "12") Long conversationId,
        @Schema(description = "대화 언어", example = "en") String language,
        @Schema(description = "검토 사유. 둘 다 해당하면 NEGATIVE", allowableValues = {"NEGATIVE", "NO_EVIDENCE"}) String reason,
        @Schema(description = "질문", nullable = true) String question,
        @Schema(description = "답변") String answer,
        @Schema(description = "답변 상태", allowableValues = {"ANSWERED", "NO_EVIDENCE", "ERROR"}) String answerStatus,
        @Schema(description = "사용자 평가 (null = 평가 안 함)", nullable = true) Boolean helpful,
        @Schema(description = "사용자 의견", nullable = true) String feedbackComment,
        @Schema(description = "답변 근거") List<SourceResponse> sources,
        @Schema(description = "관리자 처리 여부") boolean reviewed,
        @Schema(description = "관리자 메모 (자료 보충 기록)", nullable = true) String adminMemo,
        @Schema(description = "처리한 관리자 ID", nullable = true) Long reviewedBy,
        @Schema(description = "처리 시각", nullable = true) OffsetDateTime reviewedAt,
        @Schema(description = "답변 시각") OffsetDateTime createdAt
) {
}
