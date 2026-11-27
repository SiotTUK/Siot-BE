package com.siot.backend.rag.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** ADM-04: 재처리 (JSON 본문은 선택) */
@Schema(description = "ADM-04 현재 버전 재처리 (본문 생략 가능)")
public record DocumentReprocessRequest(
        @Schema(description = "적재에서 뺄 쪽 (PDF 뷰어 기준 1부터). 생략하면 현재 버전 값을 그대로 쓰고, 빈 문자열이면 빼지 않는다",
                nullable = true, example = "51-52, 100")
        String excludedPages) {
}
