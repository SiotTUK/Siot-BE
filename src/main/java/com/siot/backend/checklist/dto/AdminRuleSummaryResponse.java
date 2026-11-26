package com.siot.backend.checklist.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "관리자: 생활수칙 목록 항목 (ADM-06)")
public record AdminRuleSummaryResponse(
        @Schema(description = "항목 코드", example = "RUL-01")
        String code,
        @Schema(description = "카테고리 (한국어)", example = "쓰레기 배출")
        String category,
        @Schema(description = "제목 (한국어 원문)", example = "음식물 쓰레기 배출 방법")
        String title,
        @Schema(description = "정렬 순서", example = "1")
        int sortOrder,
        @Schema(description = "FAQ TOP 5 지정 여부 (RUL-04)")
        boolean faq,
        @Schema(description = "삭제 여부 (RUL-11). 삭제한 항목은 includeDeleted=true 일 때만 나온다")
        boolean deleted,
        @Schema(description = "삭제 시각", nullable = true)
        OffsetDateTime deletedAt,
        @Schema(description = "마지막 수정 시각", nullable = true)
        OffsetDateTime updatedAt,
        @Schema(description = "실제 안내 내용 변경 시각 (NTF-07)", nullable = true)
        OffsetDateTime noticeChangedAt,
        @Schema(description = "번역이 있는 언어 (en, zh, vi 중)", example = "[\"en\", \"zh\"]")
        List<String> translatedLanguages,
        @Schema(description = "번역이 없는 언어 (자동 번역 실패 등). 관리자가 직접 넣거나 원문을 다시 저장하면 다시 만든다",
                example = "[\"vi\"]")
        List<String> missingLanguages
) {
}
