package com.siot.backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;

/**
 * M-01: 입력 항목은 이메일(아이디), 비밀번호, 비밀번호 확인 3개.
 * 이메일은 먼저 가입 인증 코드를 확인해야 한다 (/api/auth/signup/email-verification).
 * 형식 규칙은 CredentialPolicy 에서 검사해 규칙별 오류 코드를 돌려준다.
 */
@Schema(description = "M-01 회원가입 (가입 인증 코드를 먼저 확인한 이메일만)")
public record SignupRequest(
        @Schema(description = "이메일 = 아이디. 최대 255자, 소문자로 저장", example = "user@example.com")
        @NotNull String email,
        @Schema(description = "비밀번호 (M-03). 8~64자, 영문·숫자 각 1자 이상, 영문·숫자·특수문자만, 공백 불가, 이메일과 같으면 안 됨", example = "abcd1234")
        @NotNull String password,
        @Schema(description = "비밀번호 확인 (password 와 같아야 함)", example = "abcd1234")
        @NotNull String passwordConfirm
) {
}
