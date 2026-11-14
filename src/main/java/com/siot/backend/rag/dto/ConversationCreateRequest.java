package com.siot.backend.rag.dto;

import jakarta.validation.constraints.Pattern;

/** @param language 화면 언어 (ko/en/zh/vi, 생략 시 회원 언어). 질문 언어를 판별하지 못할 때 답변 언어로 쓴다 */
public record ConversationCreateRequest(@Pattern(regexp = "ko|en|zh|vi") String language) {
}
