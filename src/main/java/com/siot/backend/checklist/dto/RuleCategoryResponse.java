package com.siot.backend.checklist.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "생활수칙 카테고리 (RUL-12, RUL-13). 피그마 M06 순서")
public record RuleCategoryResponse(
        @Schema(description = "카테고리 (한국어 원문). 목록 필터 category 값으로 쓴다 (key 를 보내도 된다)", example = "쓰레기")
        String category,
        @Schema(description = "요청 언어의 카테고리명. 번역이 없으면 한국어 원문", example = "Waste")
        String name,
        @Schema(description = "번역 여부. false 면 한국어 원문이다 (COM-02 '번역 준비 중')")
        boolean translated,
        @Schema(description = "#14 FE 키 (order, waste, traffic, tax, living, support). 관리자가 새로 만든 카테고리면 null",
                nullable = true, example = "waste")
        String key,
        @Schema(description = "#14 화면 순서 (작을수록 앞)", example = "20")
        int sortOrder
) {

    /** 키·순서 없는 카테고리 */
    public RuleCategoryResponse(String category, String name, boolean translated) {
        this(category, name, translated, null, com.siot.backend.checklist.domain.RuleCategory.DEFAULT_SORT_ORDER);
    }
}
