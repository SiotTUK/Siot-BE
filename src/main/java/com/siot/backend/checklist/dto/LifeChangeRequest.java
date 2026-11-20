package com.siot.backend.checklist.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Schema(description = "생활 변화 등록/수정 요청")
public record LifeChangeRequest(
        @Schema(description = "생략 가능. 보내도 무시하고 경로의 {code} 를 쓴다 (검증도 경로 값으로 한다)",
                example = "LC-01", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String lifeChangeCode,

        @Schema(description = "기준일 (이사일, 이직일 등)", example = "2026-11-01")
        @NotNull
        LocalDate baseDate
) {
}
