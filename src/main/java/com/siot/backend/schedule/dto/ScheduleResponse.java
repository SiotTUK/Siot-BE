package com.siot.backend.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "생활 달력 일정 응답")
public record ScheduleResponse(
        @Schema(description = "일정 ID (개인 일정인 경우 null)", example = "1")
        Long scheduleId,
        @Schema(description = "관련 가이드 항목 ID")
        Long guideId,
        @Schema(description = "일정 종류 (TAX, STAY, EVENT 등)", example = "TAX")
        String kind,
        @Schema(description = "일정 제목", example = "주민세 개인분 납부")
        String title,
        @Schema(description = "시작일", example = "2026-08-16")
        LocalDate startsOn,
        @Schema(description = "종료일", example = "2026-08-31")
        LocalDate endsOn,
        @Schema(description = "대상 설명")
        String targetDescription,
        @Schema(description = "출처 링크")
        String sourceUrl,
        @Schema(description = "개인 맞춤 일정 여부 (체류만료 등)")
        boolean personal
) {
}
