package com.siot.backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * M-01: 가입 인증 코드 요청.
 * @param language 메일 문구 언어 (ko / en / zh / vi, 선택)
 */
@Schema(description = "M-01 가입 인증 코드 요청")
public record SignupVerificationRequest(
        @Schema(description = "가입할 이메일", example = "user@example.com")
        @NotNull String email,
        @Schema(description = "메일 문구 언어. 가입 화면 언어를 보낸다 (생략 시 ko)", allowableValues = {"ko", "en", "zh", "vi"}, example = "en")
        @Size(max = 10) String language) {
}
