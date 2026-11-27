package com.siot.backend.checklist.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "체크리스트 전체 목록 및 진행률 요약")
public record ChecklistSummaryResponse(
        @Schema(description = "진행률에 넣는 필수(REQUIRED) 항목 개수. 해당 없음(NOT_APPLICABLE)으로 표시한 항목은 뺀다", example = "5")
        int totalRequiredCount,
        @Schema(description = "완료(DONE)한 필수 항목 개수", example = "2")
        int completedRequiredCount,
        @Schema(description = "completedRequiredCount ÷ totalRequiredCount 백분율 (0~100, 내림). totalRequiredCount 가 0 이면 0", example = "40")
        int progressPercentage,
        @Schema(description = "체크리스트 항목 목록")
        List<ChecklistItemResponse> items
) {
}
