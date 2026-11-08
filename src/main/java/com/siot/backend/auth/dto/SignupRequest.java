package com.siot.backend.auth.dto;

import jakarta.validation.constraints.NotNull;

/**
 * M-01: 입력 항목은 ID, 비밀번호, 비밀번호 확인 3개.
 * 형식 규칙은 CredentialPolicy 에서 검사해 규칙별 오류 코드를 돌려준다.
 */
public record SignupRequest(
        @NotNull String loginId,
        @NotNull String password,
        @NotNull String passwordConfirm
) {
}
