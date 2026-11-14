package com.siot.backend.rag.dto;

/**
 * 질문 한 번의 결과.
 *
 * @param question 저장된 질문 (개인정보는 가려진 상태)
 * @param masked   질문에서 개인정보를 가렸으면 true (CB-05, 화면 안내용)
 */
public record AskResponse(MessageResponse question, MessageResponse answer, boolean masked) {
}
