package com.siot.backend.rag.domain;

/** chat_message.answer_status (답변 메시지만) */
public enum AnswerStatus {
    /** 검색된 자료를 근거로 답변함 */
    ANSWERED,
    /** 관련 자료를 찾지 못해 답변하지 않고 문의처를 안내함 (CB-12). 관리자 미응답 질문 목록 대상 (ADM-08) */
    NO_EVIDENCE,
    /** Gemini 호출 실패로 안내 문구로 응답함 (CB-02) */
    ERROR
}
