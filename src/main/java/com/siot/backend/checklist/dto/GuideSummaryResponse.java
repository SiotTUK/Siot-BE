package com.siot.backend.checklist.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** CHK-06 관리자 항목 목록의 한 줄 */
@Schema(description = "관리자 항목 목록 (문구 수정 화면에서 항목 고르기)")
public record GuideSummaryResponse(
        @Schema(description = "항목 코드", example = "CL-01")
        String code,
        @Schema(description = "CHECKLIST / LIFE_CHANGE_TASK / RULE", example = "CHECKLIST")
        String kind,
        @Schema(description = "한국어 제목. 한국어 문구가 없으면 null", nullable = true, example = "외국인등록")
        String title,
        @Schema(description = "분류 (체크리스트 IMMIGRATION 등, 생활수칙은 한국어 카테고리)", nullable = true, example = "IMMIGRATION")
        String category,
        @Schema(description = "생활 변화 할 일의 생활 변화 코드 (LC-01 …)", nullable = true, example = "LC-01")
        String lifeChangeCode
) {
}
