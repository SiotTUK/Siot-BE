package com.siot.backend.rag.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** ADM-08: 처리 여부와 메모 (자료 보충 기록) */
public record ChatReviewUpdateRequest(@NotNull Boolean reviewed, @Size(max = 1000) String adminMemo) {
}
