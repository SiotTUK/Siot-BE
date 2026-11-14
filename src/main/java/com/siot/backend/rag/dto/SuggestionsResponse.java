package com.siot.backend.rag.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * CB-08: 추천 질문 (버튼).
 *
 * @param userType 기준이 된 사용자 유형 (NEW / LIVING, 온보딩 전이면 null)
 */
@Schema(description = "CB-08 추천 질문")
public record SuggestionsResponse(
        @Schema(description = "기준이 된 사용자 유형 (NEW / LIVING). 온보딩 전이면 null", nullable = true, example = "NEW") String userType,
        @Schema(description = "질문 언어", example = "ko") String language,
        @Schema(description = "추천 질문 3개 (버튼)") List<String> questions) {
}
