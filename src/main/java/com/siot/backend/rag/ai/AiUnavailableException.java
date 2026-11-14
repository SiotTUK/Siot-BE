package com.siot.backend.rag.ai;

/** Gemini 를 호출할 수 없을 때 (키 없음, 네트워크·쿼터 오류 등). 챗봇은 안내 문구로 응답한다 (CB-02). */
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(String message) {
        super(message);
    }

    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
