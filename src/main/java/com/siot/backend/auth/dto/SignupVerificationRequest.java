package com.siot.backend.auth.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * M-01: 가입 인증 코드 요청.
 * @param language 메일 문구 언어 (ko / en / zh / vi, 선택)
 */
public record SignupVerificationRequest(@NotNull String email, @Size(max = 10) String language) {
}
