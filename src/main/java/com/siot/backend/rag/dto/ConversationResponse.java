package com.siot.backend.rag.dto;

import java.time.OffsetDateTime;

/** @param title 첫 질문 (개인정보는 가려진 상태). 질문이 없으면 null */
public record ConversationResponse(
        Long conversationId,
        String language,
        String title,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
