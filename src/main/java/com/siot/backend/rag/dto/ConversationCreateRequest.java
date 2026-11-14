package com.siot.backend.rag.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Pattern;

/** @param language 화면 언어 (ko/en/zh/vi, 생략 시 회원 언어). 질문 언어를 판별하지 못할 때 답변 언어로 쓴다 */
@Schema(description = "CB-03 새 대화 (본문 생략 가능)")
public record ConversationCreateRequest(
        @Schema(description = "화면 언어. 생략하면 회원 언어. 질문 언어를 판별하지 못할 때 답변 언어로 쓴다", allowableValues = {"ko", "en", "zh", "vi"}, nullable = true, example = "ko")
        @Pattern(regexp = "ko|en|zh|vi") String language) {
}
