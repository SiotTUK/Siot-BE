package com.siot.backend.auth.domain;

import com.siot.backend.global.exception.BusinessException;
import com.siot.backend.global.exception.ErrorCode;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 회원 ID(이메일)·비밀번호 규칙.
 * <ul>
 *   <li>M-01: 아이디 = 이메일 (형식 검사 + 가입 인증 코드). 최대 255자</li>
 *   <li>M-03: 비밀번호 8~64자, 영문·숫자 각 1자 이상, 공백 불가, 이메일과 같으면 불가</li>
 *   <li>추가: 비밀번호는 출력 가능한 ASCII(영문·숫자·특수문자)만 허용 (bcrypt 72바이트 제한 대비)</li>
 * </ul>
 */
public final class CredentialPolicy {

    private static final int EMAIL_MAX = 255;
    /** local@domain.tld — 공백·@ 중복 불가, 도메인에 점 1개 이상 */
    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+$");
    private static final Pattern WHITESPACE = Pattern.compile("\\s");
    private static final Pattern PRINTABLE_ASCII = Pattern.compile("^[\\x21-\\x7E]*$");
    private static final Pattern LETTER = Pattern.compile("[A-Za-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");

    private static final int PASSWORD_MIN = 8;
    private static final int PASSWORD_MAX = 64;

    private CredentialPolicy() {
    }

    /** 저장·조회에 쓰는 형태 (앞뒤 공백 제거 + 소문자) */
    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /** @param email {@link #normalizeEmail} 을 거친 값 */
    public static void validateEmail(String email) {
        if (email == null || email.length() > EMAIL_MAX || !EMAIL.matcher(email).matches()) {
            throw new BusinessException(ErrorCode.EMAIL_FORMAT);
        }
    }

    public static void validatePassword(String password, String email) {
        if (password == null || password.length() < PASSWORD_MIN || password.length() > PASSWORD_MAX) {
            throw new BusinessException(ErrorCode.PASSWORD_LENGTH);
        }
        if (WHITESPACE.matcher(password).find()) {
            throw new BusinessException(ErrorCode.PASSWORD_WHITESPACE);
        }
        if (!PRINTABLE_ASCII.matcher(password).matches()) {
            throw new BusinessException(ErrorCode.PASSWORD_INVALID_CHARACTER);
        }
        if (!LETTER.matcher(password).find() || !DIGIT.matcher(password).find()) {
            throw new BusinessException(ErrorCode.PASSWORD_LETTER_AND_DIGIT_REQUIRED);
        }
        if (email != null && password.equalsIgnoreCase(email)) {
            throw new BusinessException(ErrorCode.PASSWORD_SAME_AS_EMAIL);
        }
    }
}
