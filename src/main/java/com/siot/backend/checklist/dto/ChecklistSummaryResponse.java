package com.siot.backend.checklist.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "체크리스트 전체 목록 및 진행률 요약")
public record ChecklistSummaryResponse(
        @Schema(description = "필수(REQUIRED) 항목 전체 개수", example = "5")
        int totalRequiredCount,
        @Schema(description = "완료(DONE)한 필수 항목 개수", example = "2")
        int completedRequiredCount,
        @Schema(description = "필수 항목 기준 진행률 백분율 (0~100)", example = "40")
        int progressPercentage,
        @Schema(description = "체크리스트 항목 목록")
        List<ChecklistItemResponse> items
) {
}
