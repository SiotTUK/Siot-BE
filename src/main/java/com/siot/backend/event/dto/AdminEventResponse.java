package com.siot.backend.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

/** ADM-05 관리자 행사 상세 (한국어 원문 기준, 잠금·태그·번역 상태 포함) */
@Schema(description = "ADM-05 관리자 행사 상세")
public record AdminEventResponse(
        @Schema(description = "행사 ID", example = "12") Long eventId,
        @Schema(description = "행사명 (한국어 원문)") String title,
        @Schema(description = "상세 설명 (한국어 원문)", nullable = true) String description,
        @Schema(description = "장소 (한국어 원문)", nullable = true) String venue,
        @Schema(description = "시작일", nullable = true) LocalDate startsOn,
        @Schema(description = "종료일", nullable = true) LocalDate endsOn,
        @Schema(description = "시작 시각", nullable = true) LocalTime startsAt,
        @Schema(description = "종료 시각", nullable = true) LocalTime endsAt,
        @Schema(description = "시간 미정") boolean timeUnknown,
        @Schema(description = "가격 안내", nullable = true) String priceInfo,
        @Schema(description = "신청 마감", nullable = true) OffsetDateTime applicationDeadline,
        @Schema(description = "신청 링크", nullable = true) String applicationUrl,
        @Schema(description = "원문 링크", nullable = true) String sourceUrl,
        @Schema(description = "지역", allowableValues = {"SIHEUNG", "GYEONGGI"}, nullable = true) String region,
        @Schema(description = "다문화 행사") boolean multicultural,
        @Schema(description = "출처. MANUAL = 관리자 등록") String source,
        @Schema(description = "출처 안의 ID", nullable = true) String externalId,
        @Schema(description = "상태", allowableValues = {"ACTIVE", "DELETED"}) String status,
        @Schema(description = "내용 판 번호") int revision,
        @Schema(description = "관리자가 고쳐서 수집이 덮어쓰지 않는 칸 (요청 필드 이름)",
                example = "[\"startsOn\", \"title\"]") List<String> lockedFields,
        @Schema(description = "추천 태그 (행사명 키워드로 계산)", example = "[\"FAMILY_KIDS\"]") List<String> tags,
        @Schema(description = "지금 원문 기준으로 번역이 끝난 언어 (ko 포함)", example = "[\"ko\", \"en\"]")
        List<String> translatedLanguages,
        @Schema(description = "등록 시각") OffsetDateTime createdAt,
        @Schema(description = "마지막 수정 시각") OffsetDateTime updatedAt) {
}
