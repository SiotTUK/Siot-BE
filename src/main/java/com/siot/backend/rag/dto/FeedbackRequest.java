package com.siot.backend.rag.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * CB-04: 답변 평가.
 *
 * @param helpful true = 도움이 됐어요, false = 안 됐어요
 * @param comment 선택 의견 (개인정보는 가려서 저장)
 */
@Schema(description = "CB-04 답변 평가 (다시 보내면 덮어씀)")
public record FeedbackRequest(
        @Schema(description = "true = 도움이 됐어요, false = 안 됐어요", example = "false") @NotNull Boolean helpful,
        @Schema(description = "의견 (500자 이하, 개인정보는 가려서 저장)", nullable = true, example = "주소 변경 방법이 없어요") @Size(max = 500) String comment) {
}
