package com.siot.backend.checklist.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@Schema(description = "관리자: 생활수칙 카테고리와 언어별 이름 (RUL-12, RUL-13)")
public record AdminRuleCategoryResponse(
        @Schema(description = "카테고리 (한국어 원문)", example = "쓰레기 배출")
        String category,
        @Schema(description = "이 카테고리의 생활수칙 수 (삭제한 것 제외)", example = "4")
        int ruleCount,
        @Schema(description = "언어별 이름 (en, zh, vi). 번역이 없는 언어는 빠진다")
        Map<String, String> translations
) {
}
