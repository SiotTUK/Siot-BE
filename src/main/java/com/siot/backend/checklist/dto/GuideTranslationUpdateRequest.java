package com.siot.backend.checklist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 관리자: 항목 상세 문구 저장 (CHK-06 필드, 한 언어).
 *
 * @param noticeChanged true = 실제 안내 내용 변경 (기한·장소·준비물·수수료 등, NTF-07 변경 표시 대상),
 *                      false = 오타 수정 같은 단순 수정
 */
public record GuideTranslationUpdateRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 5000) String summary,
        @Size(max = 20000) String content,
        @Size(max = 100) String badge,
        @Size(max = 2000) String place,
        @Size(max = 30) List<@NotBlank @Size(max = 255) String> docs,
        @Size(max = 255) String fee,
        @Size(max = 255) String law,
        @Size(max = 2000) String penalty,
        @Size(max = 255) String sourceName,
        @NotNull Boolean noticeChanged
) {
}
