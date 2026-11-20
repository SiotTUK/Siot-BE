package com.siot.backend.checklist.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "관리자 생활수칙 수정 요청 (RUL-10, ADM-06)")
public record RuleUpdateRequest(
        @Schema(description = "카테고리", example = "쓰레기 배출")
        String category,

        @Schema(description = "정렬 순서", example = "10")
        Integer sortOrder,

        @Schema(description = "FAQ TOP 5 표시 여부", example = "true")
        Boolean faq,

        @Schema(description = "문의 전화")
        String contact,

        @Schema(description = "출처 링크")
        String sourceUrl,

        @NotBlank
        @Schema(description = "제목 (한국어)", example = "대형 폐기물 스티커 배출 방법")
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
        String sourceName,

        @NotNull
        @Schema(description = "실제 안내 내용 변경 여부 (NTF-07: 변경 알림 발송 유도)", example = "true")
        Boolean noticeChanged
) {
}
