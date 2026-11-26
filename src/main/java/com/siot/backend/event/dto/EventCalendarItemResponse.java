package com.siot.backend.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/** EVE-03 달력에 올릴 행사 */
@Schema(description = "EVE-03 달력의 행사")
public record EventCalendarItemResponse(
        @Schema(description = "행사 ID", example = "12") Long eventId,
        @Schema(description = "행사명 (회원 언어, 없으면 한국어)", example = "시흥갯골축제") String title,
        @Schema(description = "시작일", nullable = true, example = "2026-12-18") LocalDate startsOn,
        @Schema(description = "종료일", nullable = true, example = "2026-12-20") LocalDate endsOn,
        @Schema(description = "지역", allowableValues = {"SIHEUNG", "GYEONGGI"}, nullable = true, example = "SIHEUNG")
        String region,
        @Schema(description = "회원 언어로 된 행사명이면 true", example = "true") boolean translated) {
}
