package com.siot.backend.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** ADM-05 관리자 행사 목록의 한 줄 */
@Schema(description = "ADM-05 관리자 행사 목록 항목")
public record AdminEventSummaryResponse(
        @Schema(description = "행사 ID", example = "12") Long eventId,
        @Schema(description = "행사명 (한국어 원문)", example = "시흥갯골축제") String title,
        @Schema(description = "시작일", nullable = true) LocalDate startsOn,
        @Schema(description = "종료일", nullable = true) LocalDate endsOn,
        @Schema(description = "지역", allowableValues = {"SIHEUNG", "GYEONGGI"}, nullable = true) String region,
        @Schema(description = "출처. MANUAL = 관리자 등록", allowableValues = {"GGC", "FESTIVAL_STD", "TOURAPI", "MANUAL"})
        String source,
        @Schema(description = "출처 안의 ID", nullable = true) String externalId,
        @Schema(description = "상태. DELETED 는 회원에게 보이지 않고 다시 수집해도 살아나지 않는다",
                allowableValues = {"ACTIVE", "DELETED"}) String status,
        @Schema(description = "다문화 행사") boolean multicultural,
        @Schema(description = "EVE-10 카테고리", allowableValues = {"FESTIVAL", "PERFORMANCE", "EXHIBITION", "EDUCATION", "EVENT"})
        String category,
        @Schema(description = "내용 판 번호 (바뀔 때마다 +1, 번역을 다시 만든다)", example = "1") int revision,
        @Schema(description = "마지막 수정 시각") OffsetDateTime updatedAt) {
}
