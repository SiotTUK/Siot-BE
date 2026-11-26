package com.siot.backend.checklist.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "관리자: 생활수칙 카테고리 이름 바꾸기 (RUL-12)")
public record RuleCategoryRenameRequest(
        @Schema(description = "새 한국어 카테고리명. 이미 있는 카테고리면 그 카테고리로 합친다", example = "생활 쓰레기")
        @NotBlank @Size(max = 100)
        String name
) {
}
