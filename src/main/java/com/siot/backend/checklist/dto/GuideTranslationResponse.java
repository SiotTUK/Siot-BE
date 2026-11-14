package com.siot.backend.checklist.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * @param noticeChangedAt NTF-07 실제 안내 내용이 바뀐 시각 (null = 등록 후 바뀐 적 없음)
 * @param updatedAt       CHK-06 마지막 수정 시각 (단순 수정 포함)
 */
public record GuideTranslationResponse(
        String code,
        String language,
        String title,
        String summary,
        String content,
        String badge,
        String place,
        List<String> docs,
        String fee,
        String law,
        String penalty,
        String sourceName,
        OffsetDateTime noticeChangedAt,
        OffsetDateTime updatedAt
) {
}
