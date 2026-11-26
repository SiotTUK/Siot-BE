package com.siot.backend.checklist.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "생활수칙 카테고리 (RUL-12, RUL-13)")
public record RuleCategoryResponse(
        @Schema(description = "카테고리 (한국어 원문). 목록 필터 category 값으로 쓴다", example = "쓰레기 배출")
        String category,
        @Schema(description = "요청 언어의 카테고리명. 번역이 없으면 한국어 원문", example = "Waste disposal")
        String name,
        @Schema(description = "번역 여부. false 면 한국어 원문이다 (COM-02 '번역 준비 중')")
        boolean translated
) {
}
