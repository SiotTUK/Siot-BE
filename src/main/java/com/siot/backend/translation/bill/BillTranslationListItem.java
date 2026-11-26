package com.siot.backend.translation.bill;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** RUL-07 최근 번역 목록의 한 줄 */
@Schema(description = "최근 고지서 번역 (목록용). 전체 결과는 GET /api/translations/bills/{id}")
public record BillTranslationListItem(
        @Schema(description = "최근 번역 ID", example = "12") Long id,
        @Schema(description = "요약 언어", example = "en") String language,
        @Schema(description = "서식 종류 (TAX_NOTICE / DEMAND_NOTICE / ARREARS_NOTICE / UTILITY / OTHER)",
                nullable = true, example = "TAX_NOTICE") String documentType,
        @Schema(description = "고지 기관 (원문)", nullable = true, example = "시흥시") String issuer,
        @Schema(description = "세목 (원문)", nullable = true, example = "자동차세") String taxType,
        @Schema(description = "요약 첫 줄 '무엇인지' (요약 언어)", nullable = true) String what,
        @Schema(description = "납기 내 금액 (원)", nullable = true, example = "123450") Long amount,
        @Schema(description = "납기 내 기한", nullable = true, example = "2026-12-31") LocalDate dueDate,
        @Schema(description = "납부 마감 알림을 켰는지") boolean reminder,
        @Schema(description = "마감 알림을 켤 수 있는지 (기한이 있고 지나지 않음)") boolean reminderAvailable,
        @Schema(description = "저장 시각") OffsetDateTime savedAt
) {
}
