package com.siot.backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "M-01 가입 인증 코드 확인")
public record SignupVerificationConfirmRequest(
        @Schema(description = "코드를 받은 이메일", example = "user@example.com")
        @NotBlank @Size(max = 255) String email,
        @Schema(description = "메일로 받은 6자리 숫자", example = "123456")
        @NotBlank @Pattern(regexp = "^\\s*\\d{6}\\s*$") String code
) {
}
