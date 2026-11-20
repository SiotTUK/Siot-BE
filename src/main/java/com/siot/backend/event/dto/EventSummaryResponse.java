package com.siot.backend.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalTime;

/** EVE-10 행사 목록의 한 줄 (카드). 글은 회원 언어, 번역이 없으면 한국어 원문 + translated=false */
@Schema(description = "행사 카드 (목록·추천)")
public record EventSummaryResponse(
        @Schema(description = "행사 ID. 상세 화면 /events/{eventId}", example = "12") Long eventId,
        @Schema(description = "행사명", example = "시흥갯골축제") String title,
        @Schema(description = "장소. 수집 행사는 없는 경우가 많다 (원문 링크로 안내)", nullable = true, example = "시흥갯골생태공원")
        String venue,
        @Schema(description = "시작일", nullable = true, example = "2026-12-18") LocalDate startsOn,
        @Schema(description = "종료일", nullable = true, example = "2026-12-20") LocalDate endsOn,
        @Schema(description = "시작 시각", nullable = true, example = "14:00:00") LocalTime startsAt,
        @Schema(description = "종료 시각", nullable = true, example = "17:00:00") LocalTime endsAt,
        @Schema(description = "시간 미정이면 true", example = "false") boolean timeUnknown,
        @Schema(description = "지역", allowableValues = {"SIHEUNG", "GYEONGGI"}, nullable = true, example = "SIHEUNG")
        String region,
        @Schema(description = "다문화·외국인 대상 행사", example = "false") boolean multicultural,
        @Schema(description = "글(행사명·장소)의 언어", example = "en") String language,
        @Schema(description = "회원 언어로 된 글이면 true. false 면 한국어 원문 (\"번역 준비 중\" 표시, COM-02)", example = "true")
        boolean translated) {
}
