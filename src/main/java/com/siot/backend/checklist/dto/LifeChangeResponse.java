package com.siot.backend.checklist.dto;

import com.siot.backend.checklist.domain.MemberLifeChange;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Schema(description = "등록된 생활 변화 응답")
public record LifeChangeResponse(
        @Schema(description = "생활 변화 코드", example = "LC-01")
        String lifeChangeCode,
        @Schema(description = "기준일", example = "2026-11-01")
        LocalDate baseDate,
        @Schema(description = "등록 시각")
        OffsetDateTime createdAt,
        @Schema(description = "수정 시각")
        OffsetDateTime updatedAt
) {
    public static LifeChangeResponse from(MemberLifeChange change) {
        return new LifeChangeResponse(
                change.getLifeChangeCode(),
                change.getBaseDate(),
                change.getCreatedAt(),
                change.getUpdatedAt()
        );
    }
}
