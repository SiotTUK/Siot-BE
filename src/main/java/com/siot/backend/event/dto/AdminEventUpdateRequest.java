package com.siot.backend.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * EVE-08 관리자 행사 수정. 보내지 않은(null) 칸은 그대로. 보낸 칸은 잠겨서 다시 수집해도 덮어쓰지 않는다.
 * 글 칸(설명·장소·가격·링크)은 빈 문자열("")을 보내면 비운다.
 */
@Schema(description = "EVE-08 행사 수정 (보낸 칸만 바뀌고 잠김)")
public record AdminEventUpdateRequest(
        @Schema(description = "행사명", nullable = true) @Size(min = 1, max = 255) String title,
        @Schema(description = "상세 설명. \"\" 이면 비운다", nullable = true) @Size(max = 10000) String description,
        @Schema(description = "장소. \"\" 이면 비운다", nullable = true) @Size(max = 255) String venue,
        @Schema(description = "시작일", nullable = true) LocalDate startsOn,
        @Schema(description = "종료일", nullable = true) LocalDate endsOn,
        @Schema(description = "시작 시각", nullable = true) LocalTime startsAt,
        @Schema(description = "종료 시각", nullable = true) LocalTime endsAt,
        @Schema(description = "가격 안내. \"\" 이면 비운다", nullable = true) @Size(max = 1000) String priceInfo,
        @Schema(description = "신청 마감", nullable = true) OffsetDateTime applicationDeadline,
        @Schema(description = "신청 링크 (http/https). \"\" 이면 비운다", nullable = true)
        @Size(max = 2000) @Pattern(regexp = URL_OR_EMPTY) String applicationUrl,
        @Schema(description = "원문 링크 (http/https). \"\" 이면 비운다", nullable = true)
        @Size(max = 2000) @Pattern(regexp = URL_OR_EMPTY) String sourceUrl,
        @Schema(description = "지역", allowableValues = {"SIHEUNG", "GYEONGGI"}, nullable = true)
        @Pattern(regexp = AdminEventCreateRequest.REGION_PATTERN) String region,
        @Schema(description = "다문화·외국인 대상 행사", nullable = true) Boolean multicultural) {

    public static final String URL_OR_EMPTY = "^$|^https?://\\S+$";
}
