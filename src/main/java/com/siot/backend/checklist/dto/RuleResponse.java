package com.siot.backend.checklist.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "생활수칙 응답")
public record RuleResponse(
        @Schema(description = "항목 코드 (FE 주소 /rules/{code})", example = "waste-food")
        String code,
        @Schema(description = "카테고리 (한국어 원문, 목록 필터 값)", example = "쓰레기 배출")
        String category,
        @Schema(description = "요청 언어의 카테고리명 (RUL-13). 번역이 없으면 한국어 원문", example = "Waste disposal")
        String categoryName,
        @Schema(description = "제목", example = "음식물 쓰레기 배출 방법")
        String title,
        @Schema(description = "요약")
        String summary,
        @Schema(description = "본문 (points 를 줄바꿈으로 이은 글. 화면은 points 를 쓴다)")
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
        @Schema(description = "문의처 (요청 언어 문구, 없으면 공통 문의 전화)")
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
        String sourcePages,

        // #14 FE 구조
        @Schema(description = "항목 ID. 오류 신고(POST /api/reports)의 guideId 로 보낸다 (guideCode 로 code 를 보내도 된다)", example = "41")
        Long guideId,
        @Schema(description = "카테고리 FE 키 (order, waste, traffic, tax, living, support). 관리자가 새로 만든 카테고리면 null",
                nullable = true, example = "waste")
        String categoryKey,
        @Schema(description = "확인된 문장 목록 (요청 언어, 순서대로). 화면 본문")
        List<String> points,
        @Schema(description = "시기 (요청 언어)", nullable = true)
        String when,
        @Schema(description = "출처 목록 (공식 페이지 이름·링크, 순서대로). 이름은 한국어 페이지 제목")
        List<RuleSourceResponse> sources,
        @Schema(description = "공식 페이지와 대조해 확인한 날", nullable = true, example = "2026-11-25")
        LocalDate checkedOn,
        @Schema(description = "이 언어 문구가 있는지. false 면 한국어 원문 (COM-02 '번역 준비 중')")
        boolean translated
) {
}
