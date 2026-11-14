package com.siot.backend.checklist.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * @param noticeChangedAt NTF-07 실제 안내 내용이 바뀐 시각 (null = 등록 후 바뀐 적 없음)
 * @param updatedAt       CHK-06 마지막 수정 시각 (단순 수정 포함)
 */
@Schema(description = "항목 상세 문구 (한 언어)")
public record GuideTranslationResponse(
        @Schema(description = "항목 ID", example = "CL-01") String code,
        @Schema(description = "문구 언어", example = "ko") String language,
        @Schema(description = "제목", example = "외국인등록") String title,
        @Schema(description = "요약", nullable = true) String summary,
        @Schema(description = "본문", nullable = true) String content,
        @Schema(description = "배지 문구", nullable = true, example = "입국 후 90일 이내") String badge,
        @Schema(description = "신청 장소", nullable = true) String place,
        @Schema(description = "준비물 (없으면 빈 목록)") List<String> docs,
        @Schema(description = "수수료", nullable = true) String fee,
        @Schema(description = "근거 법령", nullable = true) String law,
        @Schema(description = "안 하면 받는 불이익", nullable = true) String penalty,
        @Schema(description = "출처 이름", nullable = true) String sourceName,
        @Schema(description = "실제 안내 내용이 바뀐 시각 (NTF-07). null = 등록 후 바뀐 적 없음", nullable = true) OffsetDateTime noticeChangedAt,
        @Schema(description = "마지막 수정 시각 (CHK-06, 단순 수정 포함)") OffsetDateTime updatedAt
) {
}
