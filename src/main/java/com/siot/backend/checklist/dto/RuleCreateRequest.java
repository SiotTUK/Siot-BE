package com.siot.backend.checklist.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

@Schema(description = "관리자 생활수칙 등록 요청 (RUL-09, ADM-06)")
public record RuleCreateRequest(
        @NotBlank
        @Schema(description = "항목 코드 (예: RUL-09)", example = "RUL-09")
        String code,

        @NotBlank
        @Schema(description = "카테고리 (쓰레기 배출, 교통 질서 등)", example = "쓰레기 배출")
        String category,

        @Schema(description = "정렬 순서", example = "10")
        int sortOrder,

        @Schema(description = "FAQ TOP 5 표시 여부", example = "false")
        boolean faq,

        @Schema(description = "문의 전화", example = "031-310-2114")
        String contact,

        @Schema(description = "출처 링크", example = "https://www.siheung.go.kr")
        String sourceUrl,

        @NotBlank
        @Schema(description = "제목 (한국어 원문)", example = "대형 폐기물 스티커 배출 방법")
        String title,

        @Schema(description = "요약")
        String summary,

        @Schema(description = "본문")
        String content,

        @Schema(description = "배지 문구")
        String badge,

        @Schema(description = "배출/신청 장소")
        String place,

        @Schema(description = "준비물 목록")
        List<String> docs,

        @Schema(description = "수수료")
        String fee,

        @Schema(description = "근거 법령")
        String law,

        @Schema(description = "과태료 및 불이익")
        String penalty,

        @Schema(description = "출처 이름")
        String sourceName
) {
}
