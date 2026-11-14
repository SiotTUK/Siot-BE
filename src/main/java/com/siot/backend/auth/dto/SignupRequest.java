package com.siot.backend.auth.dto;

import jakarta.validation.constraints.NotNull;

/**
 * M-01: 입력 항목은 이메일(아이디), 비밀번호, 비밀번호 확인 3개.
 * 이메일은 먼저 가입 인증 코드를 확인해야 한다 (/api/auth/signup/email-verification).
 * 형식 규칙은 CredentialPolicy 에서 검사해 규칙별 오류 코드를 돌려준다.
 */
public record SignupRequest(
        @NotNull String email,
        @NotNull String password,
        @NotNull String passwordConfirm
) {
}
