package com.siot.backend.rag.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * @param role         USER / ASSISTANT
 * @param answerStatus 답변만: ANSWERED / NO_EVIDENCE / ERROR
 * @param sources      답변 근거 (ANSWERED 일 때만)
 */
public record MessageResponse(
        Long messageId,
        String role,
        String content,
        String answerStatus,
        List<SourceResponse> sources,
        OffsetDateTime createdAt
) {
}
