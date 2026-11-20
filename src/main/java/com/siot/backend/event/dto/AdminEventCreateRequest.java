package com.siot.backend.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

/** EVE-07 관리자 행사 등록. 글은 한국어 원문 (en/zh/vi 는 저장할 때 자동 번역). 시작일·종료일 중 하나는 필수 */
@Schema(description = "EVE-07 행사 등록 (한국어 원문)")
public record AdminEventCreateRequest(
        @Schema(description = "행사명", example = "외국인 주민 한국어 교실 개강식")
        @NotBlank @Size(max = 255) String title,
        @Schema(description = "상세 설명", nullable = true) @Size(max = 10000) String description,
        @Schema(description = "장소", nullable = true, example = "시흥시 외국인복지센터") @Size(max = 255) String venue,
        @Schema(description = "시작일 (시작일·종료일 중 하나는 필수)", nullable = true, example = "2026-12-18") LocalDate startsOn,
        @Schema(description = "종료일. 없으면 시작일과 같은 날", nullable = true, example = "2026-12-18") LocalDate endsOn,
        @Schema(description = "시작 시각. 시각을 모두 비우면 시간 미정", nullable = true, example = "14:00") LocalTime startsAt,
        @Schema(description = "종료 시각", nullable = true, example = "16:00") LocalTime endsAt,
        @Schema(description = "가격 안내", nullable = true, example = "무료") @Size(max = 1000) String priceInfo,
        @Schema(description = "신청 마감", nullable = true, example = "2026-12-10T18:00:00+09:00")
        OffsetDateTime applicationDeadline,
        @Schema(description = "신청 링크 (http/https)", nullable = true)
        @Size(max = 2000) @Pattern(regexp = URL_PATTERN) String applicationUrl,
        @Schema(description = "원문 링크 (http/https)", nullable = true)
        @Size(max = 2000) @Pattern(regexp = URL_PATTERN) String sourceUrl,
        @Schema(description = "지역", allowableValues = {"SIHEUNG", "GYEONGGI"}, example = "SIHEUNG")
        @NotNull @Pattern(regexp = REGION_PATTERN) String region,
        @Schema(description = "다문화·외국인 대상 행사. 비우면 행사명·설명 키워드로 정한다", nullable = true, example = "true")
        Boolean multicultural) {

    public static final String URL_PATTERN = "^https?://\\S+$";
    public static final String REGION_PATTERN = "SIHEUNG|GYEONGGI";
}
