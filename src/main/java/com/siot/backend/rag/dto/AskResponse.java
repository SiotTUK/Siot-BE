package com.siot.backend.rag.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 질문 한 번의 결과.
 *
 * @param question 저장된 질문 (개인정보는 가려진 상태)
 * @param masked   질문에서 개인정보를 가렸으면 true (CB-05, 화면 안내용)
 */
@Schema(description = "CB-16 질문 한 번의 결과")
public record AskResponse(
        @Schema(description = "저장된 질문 (개인정보는 가린 값)") MessageResponse question,
        @Schema(description = "답변") MessageResponse answer,
        @Schema(description = "질문에서 개인정보를 가렸으면 true. 화면에 '개인정보는 가려서 저장했어요' 안내 (CB-05)", example = "false") boolean masked) {
}
