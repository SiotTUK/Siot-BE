package com.siot.backend.checklist.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

@Schema(description = "생활 변화 등록/수정 요청")
public record LifeChangeRequest(
        @Schema(description = "생활 변화 코드 (LC-01 ~ LC-08)", example = "LC-01")
        @NotNull
        @Pattern(regexp = "^LC-0[1-8]$", message = "올바른 생활 변화 코드를 입력해 주세요 (LC-01 ~ LC-08)")
        String lifeChangeCode,

        @Schema(description = "기준일 (이사일, 이직일 등)", example = "2026-11-01")
        @NotNull
        LocalDate baseDate
) {
}
