package com.siot.backend.checklist.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "관리자: 생활수칙 카테고리명 번역 고치기 (RUL-13)")
public record RuleCategoryTranslationRequest(
        @Schema(description = "이 언어의 카테고리명", example = "Waste disposal")
        @NotBlank @Size(max = 100)
        String name
) {
}
