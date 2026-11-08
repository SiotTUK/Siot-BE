package com.siot.backend.auth.domain;

import com.siot.backend.global.exception.BusinessException;
import com.siot.backend.global.exception.ErrorCode;

import java.util.regex.Pattern;

/**
 * 회원 ID·비밀번호 규칙.
 * <ul>
 *   <li>M-01: ID는 영문 소문자와 숫자만, 4~20자</li>
 *   <li>M-03: 비밀번호 8~64자, 영문·숫자 각 1자 이상, 공백 불가, ID와 같으면 불가</li>
 *   <li>추가: 비밀번호는 출력 가능한 ASCII(영문·숫자·특수문자)만 허용 (bcrypt 72바이트 제한 대비)</li>
 * </ul>
 */
public final class CredentialPolicy {

    private static final Pattern LOGIN_ID = Pattern.compile("^[a-z0-9]{4,20}$");
    private static final Pattern WHITESPACE = Pattern.compile("\\s");
    private static final Pattern PRINTABLE_ASCII = Pattern.compile("^[\\x21-\\x7E]*$");
    private static final Pattern LETTER = Pattern.compile("[A-Za-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");

    private static final int PASSWORD_MIN = 8;
    private static final int PASSWORD_MAX = 64;

    private CredentialPolicy() {
    }

    public static void validateLoginId(String loginId) {
        if (loginId == null || !LOGIN_ID.matcher(loginId).matches()) {
            throw new BusinessException(ErrorCode.LOGIN_ID_FORMAT);
        }
    }

    public static void validatePassword(String password, String loginId) {
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
        if (password.equals(loginId)) {
            throw new BusinessException(ErrorCode.PASSWORD_SAME_AS_LOGIN_ID);
        }
    }
}
