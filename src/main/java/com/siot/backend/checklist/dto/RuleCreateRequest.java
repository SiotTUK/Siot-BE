package com.siot.backend.checklist.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 필수: 코드, 카테고리, 제목, 본문. 원문 링크·문의처 등은 선택 (RUL-09).
 * 길이 제한은 DB 칸과 관리자 문구 저장(GuideTranslationUpdateRequest)과 같다.
 */
@Schema(description = "관리자 생활수칙 등록 요청 (RUL-09, ADM-06)")
public record RuleCreateRequest(
        @NotBlank
        @Size(max = 50)
        @Schema(description = "항목 코드 (예: RUL-09). 삭제한 생활수칙이나 다른 항목이 쓰던 코드는 쓸 수 없다", example = "RUL-09")
        String code,

        @NotBlank
        @Size(max = 100)
        @Schema(description = "카테고리 (쓰레기 배출, 교통 질서 등)", example = "쓰레기 배출")
        String category,

        @Schema(description = "정렬 순서", example = "10")
        int sortOrder,

        @Schema(description = "FAQ TOP 5 표시 여부", example = "false")
        boolean faq,

        @Size(max = 255)
        @Schema(description = "문의 전화", example = "031-310-2114")
        String contact,

        @Size(max = 2000)
        @Pattern(regexp = SOURCE_URL_PATTERN, message = "출처 링크는 http:// 또는 https:// 로 시작해야 합니다.")
        @Schema(description = "출처 링크 (http/https)", example = "https://www.siheung.go.kr")
        String sourceUrl,

        @NotBlank
        @Size(max = 255)
        @Schema(description = "제목 (한국어 원문)", example = "대형 폐기물 스티커 배출 방법")
        String title,

        @Size(max = 5000)
        @Schema(description = "요약")
        String summary,

        @NotBlank
        @Size(max = 20000)
        @Schema(description = "본문 (한국어 원문)")
        String content,

        @Size(max = 100)
        @Schema(description = "배지 문구")
        String badge,

        @Size(max = 2000)
        @Schema(description = "배출/신청 장소")
        String place,

        @Size(max = 30)
        @Schema(description = "준비물 목록 (최대 30개, 각 255자)")
        List<@NotBlank @Size(max = 255) String> docs,

        @Size(max = 255)
        @Schema(description = "수수료")
        String fee,

        @Size(max = 255)
        @Schema(description = "근거 법령")
        String law,

        @Size(max = 2000)
        @Schema(description = "과태료 및 불이익")
        String penalty,

        @Size(max = 255)
        @Schema(description = "출처 이름")
        String sourceName
) {
    /** 비어 있거나 http(s) 링크 (javascript: 등 막기) */
    public static final String SOURCE_URL_PATTERN = "^$|^\\s*https?://\\S+\\s*$";
}
