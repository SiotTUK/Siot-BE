package com.siot.backend.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

/** EVE-04 행사 상세. 값이 없는 칸은 null (수집 행사는 장소·설명이 없는 경우가 많아 원문 링크로 안내) */
@Schema(description = "EVE-04 행사 상세")
public record EventDetailResponse(
        @Schema(description = "행사 ID", example = "12") Long eventId,
        @Schema(description = "행사명", example = "시흥갯골축제") String title,
        @Schema(description = "상세 설명", nullable = true) String description,
        @Schema(description = "장소", nullable = true, example = "시흥갯골생태공원") String venue,
        @Schema(description = "시작일", nullable = true, example = "2026-12-18") LocalDate startsOn,
        @Schema(description = "종료일", nullable = true, example = "2026-12-20") LocalDate endsOn,
        @Schema(description = "시작 시각", nullable = true, example = "14:00:00") LocalTime startsAt,
        @Schema(description = "종료 시각", nullable = true, example = "17:00:00") LocalTime endsAt,
        @Schema(description = "시간 미정이면 true", example = "false") boolean timeUnknown,
        @Schema(description = "가격 안내 (원문 그대로)", nullable = true, example = "무료") String priceInfo,
        @Schema(description = "신청 마감", nullable = true, example = "2026-12-10T18:00:00+09:00")
        OffsetDateTime applicationDeadline,
        @Schema(description = "신청 링크", nullable = true) String applicationUrl,
        @Schema(description = "원문 링크", nullable = true, example = "https://www.siheung.go.kr") String sourceUrl,
        @Schema(description = "지역", allowableValues = {"SIHEUNG", "GYEONGGI"}, nullable = true, example = "SIHEUNG")
        String region,
        @Schema(description = "다문화·외국인 대상 행사", example = "false") boolean multicultural,
        @Schema(description = "내가 이 행사 알림을 켜 두었으면 true (EVE-06)", example = "false") boolean subscribed,
        @Schema(description = "글(행사명·설명·장소)의 언어", example = "en") String language,
        @Schema(description = "회원 언어로 된 글이면 true. false 면 한국어 원문 (\"번역 준비 중\", COM-02)", example = "true")
        boolean translated) {
}
