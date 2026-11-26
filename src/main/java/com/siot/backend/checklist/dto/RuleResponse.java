package com.siot.backend.checklist.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "생활수칙 응답")
public record RuleResponse(
        @Schema(description = "항목 코드", example = "RUL-01")
        String code,
        @Schema(description = "카테고리 (한국어 원문, 목록 필터 값)", example = "쓰레기 배출")
        String category,
        @Schema(description = "요청 언어의 카테고리명 (RUL-13). 번역이 없으면 한국어 원문", example = "Waste disposal")
        String categoryName,
        @Schema(description = "제목", example = "음식물 쓰레기 배출 방법")
        String title,
        @Schema(description = "요약")
        String summary,
        @Schema(description = "본문")
        String content,
        @Schema(description = "배지")
        String badge,
        @Schema(description = "신청/배출 장소")
        String place,
        @Schema(description = "준비물")
        List<String> docs,
        @Schema(description = "수수료")
        String fee,
        @Schema(description = "근거 법령")
        String law,
        @Schema(description = "불이익")
        String penalty,
        @Schema(description = "문의 전화")
        String contact,
        @Schema(description = "출처 링크")
        String sourceUrl,
        @Schema(description = "출처 이름")
        String sourceName,
        @Schema(description = "정렬 순서")
        int sortOrder,
        @Schema(description = "자주 묻는 질문(FAQ) 여부")
        boolean faq,
        @Schema(description = "안내 내용 변경 시각")
        OffsetDateTime noticeChangedAt,

        // CHK-06 출처 쪽수
        @Schema(description = "CHK-06 출처 안내책자 시작 쪽수", example = "20")
        Integer pageStart,
        @Schema(description = "CHK-06 출처 안내책자 종료 쪽수", example = "22")
        Integer pageEnd,
        @Schema(description = "CHK-06 출처 쪽수 표기", example = "p.20~22")
        String sourcePages
) {
}
