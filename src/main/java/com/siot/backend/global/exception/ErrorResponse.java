package com.siot.backend.global.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * 실패 응답: {@code {"code": ..., "message": ...}}.
 * 화면에 추가 정보가 필요한 오류만 {@code details} 를 넣는다 (예: LOGIN_FAILED 의 실패 횟수). 없으면 응답에서 빠진다.
 */
public record ErrorResponse(
        String code,
        String message,
        @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Object> details) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), null);
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.name(), message, null);
    }

    public static ErrorResponse of(ErrorCode errorCode, String message, Map<String, Object> details) {
        return new ErrorResponse(errorCode.name(), message, details);
    }
}
