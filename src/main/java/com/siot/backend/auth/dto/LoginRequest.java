package com.siot.backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "M-08 로그인")
public record LoginRequest(
        @Schema(description = "가입 이메일 (대소문자 무시)", example = "user@example.com")
        @NotBlank @Size(max = 255) String email,
        @Schema(description = "비밀번호", example = "abcd1234")
        @NotBlank @Size(max = 64) String password
) {
}
