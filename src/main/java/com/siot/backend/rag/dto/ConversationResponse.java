package com.siot.backend.rag.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/** @param title 첫 질문 (개인정보는 가려진 상태). 질문이 없으면 null */
@Schema(description = "챗봇 대화")
public record ConversationResponse(
        @Schema(description = "대화 ID", example = "12") Long conversationId,
        @Schema(description = "대화 언어", example = "ko") String language,
        @Schema(description = "첫 질문 (개인정보는 가린 값). 질문이 없으면 null", nullable = true, example = "외국인등록은 언제까지 해야 하나요?") String title,
        @Schema(description = "시작 시각") OffsetDateTime createdAt,
        @Schema(description = "마지막 대화 시각") OffsetDateTime updatedAt
) {
}
