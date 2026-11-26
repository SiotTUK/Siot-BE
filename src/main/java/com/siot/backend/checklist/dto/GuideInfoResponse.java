package com.siot.backend.checklist.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "관리자: 항목의 출처 링크·문의 전화")
public record GuideInfoResponse(
        @Schema(description = "항목 코드", example = "CL-01")
        String code,
        @Schema(description = "CHECKLIST / LIFE_CHANGE_TASK / RULE", example = "CHECKLIST")
        String kind,
        @Schema(description = "출처 링크", nullable = true)
        String sourceUrl,
        @Schema(description = "문의 전화", nullable = true)
        String contact,
        @Schema(description = "마지막 수정 시각", nullable = true)
        OffsetDateTime updatedAt
) {
}
