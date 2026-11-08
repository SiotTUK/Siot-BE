package com.siot.backend.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INVALID_INPUT(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    CSRF_TOKEN_INVALID(HttpStatus.FORBIDDEN, "보안 토큰이 없거나 만료되었습니다. 페이지를 새로고침한 뒤 다시 시도해 주세요."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

    // ---- auth / member (M-01 ~ M-03, M-08) ----
    // 프론트엔드는 code 로 사용자 언어 문구를 표시한다 (M-03: 어떤 규칙을 어겼는지 사용자 언어로 안내)
    LOGIN_ID_FORMAT(HttpStatus.BAD_REQUEST, "ID는 영문 소문자와 숫자 4~20자로 입력해 주세요."),
    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "이미 사용 중인 ID입니다."),
    PASSWORD_LENGTH(HttpStatus.BAD_REQUEST, "비밀번호는 8~64자로 입력해 주세요."),
    PASSWORD_LETTER_AND_DIGIT_REQUIRED(HttpStatus.BAD_REQUEST, "비밀번호에 영문과 숫자를 각각 1자 이상 포함해 주세요."),
    PASSWORD_WHITESPACE(HttpStatus.BAD_REQUEST, "비밀번호에 공백을 사용할 수 없습니다."),
    PASSWORD_INVALID_CHARACTER(HttpStatus.BAD_REQUEST, "비밀번호에는 영문, 숫자, 특수문자만 사용할 수 있습니다."),
    PASSWORD_SAME_AS_LOGIN_ID(HttpStatus.BAD_REQUEST, "ID와 같은 비밀번호는 사용할 수 없습니다."),
    PASSWORD_CONFIRM_MISMATCH(HttpStatus.BAD_REQUEST, "비밀번호가 일치하지 않습니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "ID 또는 비밀번호가 맞지 않습니다."),
    LOGIN_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "로그인에 5회 연속 실패하여 5분 동안 로그인할 수 없습니다."),
    // 401 은 프론트엔드가 "로그인 만료" 로 처리하므로 비밀번호 재확인 실패는 400 으로 응답한다
    PASSWORD_INCORRECT(HttpStatus.BAD_REQUEST, "비밀번호가 맞지 않습니다.");

    private final HttpStatus status;
    private final String message;
}
