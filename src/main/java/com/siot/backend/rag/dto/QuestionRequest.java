package com.siot.backend.rag.dto;

import jakarta.validation.constraints.NotBlank;

/** 길이 제한은 siot.chatbot.max-question-length (서비스에서 검사) */
public record QuestionRequest(@NotBlank String content) {
}
