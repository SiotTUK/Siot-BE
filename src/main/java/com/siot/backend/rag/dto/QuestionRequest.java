package com.siot.backend.rag.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;

/** 길이 제한은 siot.chatbot.max-question-length (서비스에서 검사) */
@Schema(description = "CB-16 질문")
public record QuestionRequest(
        @Schema(description = "질문 (1~1000자). 개인정보는 가려서 저장한다", example = "외국인등록은 언제까지 해야 하나요?")
        @NotBlank String content) {
}
