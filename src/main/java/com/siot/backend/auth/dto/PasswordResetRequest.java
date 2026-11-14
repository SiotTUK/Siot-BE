package com.siot.backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** M-11: 비밀번호 찾기에서 이메일(아이디) 입력 */
@Schema(description = "M-11 재설정 링크 요청")
public record PasswordResetRequest(
        @Schema(description = "가입한 이메일", example = "user@example.com")
        @NotBlank @Size(max = 255) String email) {
}
