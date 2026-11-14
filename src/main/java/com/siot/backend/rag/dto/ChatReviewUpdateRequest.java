package com.siot.backend.rag.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** ADM-08: 처리 여부와 메모 (자료 보충 기록) */
@Schema(description = "ADM-08 처리 기록")
public record ChatReviewUpdateRequest(
        @Schema(description = "처리 여부", example = "true") @NotNull Boolean reviewed,
        @Schema(description = "메모 (1000자 이하, 빈 값이면 지움)", nullable = true, example = "자동차세 납부 기간 자료 추가함") @Size(max = 1000) String adminMemo) {
}
