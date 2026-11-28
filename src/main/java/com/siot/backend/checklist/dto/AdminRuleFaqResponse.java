package com.siot.backend.checklist.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * 관리자 FAQ (RUL-04, ADM-06).
 *
 * @param ruleDeleted 연결한 생활수칙을 지웠으면 true (회원 화면에서는 연결 없이 보인다)
 */
@Schema(description = "관리자: 생활수칙 FAQ와 언어별 질문·답변 (RUL-04, ADM-06)")
public record AdminRuleFaqResponse(
        @Schema(description = "FAQ ID", example = "1") Long faqId,
        @Schema(description = "순서 (작을수록 앞)", example = "1") int sortOrder,
        @Schema(description = "자세히 볼 생활수칙 코드. 연결이 없으면 null", nullable = true, example = "waste-food")
        String ruleCode,
        @Schema(description = "자세히 볼 생활수칙 제목 (한국어)", nullable = true, example = "음식물 쓰레기")
        String ruleTitle,
        @Schema(description = "연결한 생활수칙을 지웠으면 true (회원 화면에서는 연결 없이 보인다)") boolean ruleDeleted,
        @Schema(description = "언어(ko, en, zh, vi)별 질문·답변. 없는 언어는 빠진다")
        Map<String, RuleFaqRequest.Text> translations) {
}
