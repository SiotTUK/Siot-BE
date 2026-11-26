package com.siot.backend.checklist.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "#14 RUL-04 생활수칙 자주 묻는 질문")
public record RuleFaqResponse(
        @Schema(description = "순서 (1부터)", example = "1") int sortOrder,
        @Schema(description = "질문 (요청 언어, 없으면 한국어)", example = "음식물 쓰레기는 어디에 버려요?") String question,
        @Schema(description = "답변 (요청 언어, 없으면 한국어)") String answer,
        @Schema(description = "자세히 볼 생활수칙 코드 (/rules/{code}). 그 항목을 지웠으면 null", nullable = true, example = "waste-food")
        String ruleCode,
        @Schema(description = "자세히 볼 생활수칙 제목 (요청 언어)", nullable = true, example = "음식물 쓰레기") String ruleTitle,
        @Schema(description = "질문·답변 언어. 요청 언어 문구가 없으면 ko", example = "en") String language) {
}
