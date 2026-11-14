package com.siot.backend.rag.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * CB-17: 답변 근거. 화면에는 documentName 만 보이고, 누르면 쪽수와 원문 링크를 펼친다.
 * 문서가 삭제된 경우 documentName 등은 null.
 */
@Schema(description = "CB-17 답변 근거. 화면에는 documentName 만 보이고 누르면 쪽수·원문 링크를 펼친다")
public record SourceResponse(
        @Schema(description = "표시 순서 (1부터)", example = "1") int order,
        @Schema(description = "문서 이름. 문서가 삭제됐으면 null", nullable = true, example = "시흥시 외국인주민 생활안내") String documentName,
        @Schema(description = "시작 쪽 (PDF 기준 1부터)", nullable = true, example = "12") Integer pageStart,
        @Schema(description = "끝 쪽", nullable = true, example = "13") Integer pageEnd,
        @Schema(description = "원문 링크", nullable = true) String sourceUrl,
        @Schema(description = "관련도 (0~1, 높을수록 관련)", nullable = true, example = "0.82") Double relevanceScore
) {
}
