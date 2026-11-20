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
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "요청 본문은 JSON(Content-Type: application/json)으로 보내 주세요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

    // ---- auth / member (M-01 ~ M-03, M-08) — 아이디 = 이메일 ----
    // 프론트엔드는 code 로 사용자 언어 문구를 표시한다 (M-03: 어떤 규칙을 어겼는지 사용자 언어로 안내)
    EMAIL_FORMAT(HttpStatus.BAD_REQUEST, "이메일 형식으로 입력해 주세요."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 가입한 이메일입니다."),
    PASSWORD_LENGTH(HttpStatus.BAD_REQUEST, "비밀번호는 8~64자로 입력해 주세요."),
    PASSWORD_LETTER_AND_DIGIT_REQUIRED(HttpStatus.BAD_REQUEST, "비밀번호에 영문과 숫자를 각각 1자 이상 포함해 주세요."),
    PASSWORD_WHITESPACE(HttpStatus.BAD_REQUEST, "비밀번호에 공백을 사용할 수 없습니다."),
    PASSWORD_INVALID_CHARACTER(HttpStatus.BAD_REQUEST, "비밀번호에는 영문, 숫자, 특수문자만 사용할 수 있습니다."),
    PASSWORD_SAME_AS_EMAIL(HttpStatus.BAD_REQUEST, "이메일과 같은 비밀번호는 사용할 수 없습니다."),
    PASSWORD_CONFIRM_MISMATCH(HttpStatus.BAD_REQUEST, "비밀번호가 일치하지 않습니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 맞지 않습니다."),
    LOGIN_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "로그인에 5회 연속 실패하여 5분 동안 로그인할 수 없습니다."),
    // 401 은 프론트엔드가 "로그인 만료" 로 처리하므로 비밀번호 재확인 실패는 400 으로 응답한다
    PASSWORD_INCORRECT(HttpStatus.BAD_REQUEST, "비밀번호가 맞지 않습니다."),

    // ---- 관리자 (ADM) ----
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),
    ADMIN_SELF_DEMOTION(HttpStatus.BAD_REQUEST, "자기 자신의 관리자 권한은 내릴 수 없습니다."),
    CHAT_REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "검토 대상 답변을 찾을 수 없습니다."),
    GUIDE_NOT_FOUND(HttpStatus.NOT_FOUND, "항목을 찾을 수 없습니다."),
    GUIDE_TRANSLATION_NOT_FOUND(HttpStatus.NOT_FOUND, "이 언어의 문구가 아직 없습니다."),

    // ---- 이메일 인증 (M-01 가입 인증 코드, NTF-09) ----
    EMAIL_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "이메일 인증을 먼저 완료해 주세요."),
    EMAIL_ALREADY_VERIFIED(HttpStatus.CONFLICT, "이미 인증한 이메일입니다."),
    EMAIL_VERIFICATION_TOO_FREQUENT(HttpStatus.TOO_MANY_REQUESTS, "인증 코드는 1분에 한 번만 요청할 수 있습니다."),
    EMAIL_VERIFICATION_DAILY_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "오늘 인증 코드 요청 횟수를 모두 사용했습니다. 내일 다시 시도해 주세요."),
    EMAIL_VERIFICATION_CODE_INVALID(HttpStatus.BAD_REQUEST, "인증 코드가 맞지 않습니다."),
    EMAIL_VERIFICATION_EXPIRED(HttpStatus.BAD_REQUEST, "인증 코드가 만료되었습니다. 코드를 다시 요청해 주세요."),

    // ---- 비밀번호 재설정 (M-11) ----
    PASSWORD_RESET_TOO_FREQUENT(HttpStatus.TOO_MANY_REQUESTS, "비밀번호 재설정은 1분에 한 번만 요청할 수 있습니다."),
    PASSWORD_RESET_DAILY_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "오늘 비밀번호 재설정 요청 횟수를 모두 사용했습니다. 내일 다시 시도해 주세요."),
    PASSWORD_RESET_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "재설정 링크가 만료되었거나 이미 사용되었습니다. 다시 요청해 주세요."),

    // ---- 챗봇 (CB) ----
    CHAT_CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "대화를 찾을 수 없습니다."),
    CHAT_QUESTION_LENGTH(HttpStatus.BAD_REQUEST, "질문은 1~1000자로 입력해 주세요."),
    CHAT_MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "메시지를 찾을 수 없습니다."),
    CHAT_FEEDBACK_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "챗봇 답변만 평가할 수 있습니다."),

    // ---- 생활정보 · 체크리스트 · 알림 · 신고 (역할 A) ----
    CONTENT_REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "오류 신고를 찾을 수 없습니다."),
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다."),
    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "일정을 찾을 수 없습니다."),
    LIFE_CHANGE_NOT_FOUND(HttpStatus.NOT_FOUND, "생활 변화 기록을 찾을 수 없습니다."),
    INVALID_LIFE_CHANGE_CODE(HttpStatus.BAD_REQUEST, "올바르지 않은 생활 변화 코드입니다.");

    private final HttpStatus status;
    private final String message;
}
