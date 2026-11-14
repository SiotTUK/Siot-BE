package com.siot.backend.rag.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * CB-04: 답변 평가.
 *
 * @param helpful true = 도움이 됐어요, false = 안 됐어요
 * @param comment 선택 의견 (개인정보는 가려서 저장)
 */
public record FeedbackRequest(@NotNull Boolean helpful, @Size(max = 500) String comment) {
}
