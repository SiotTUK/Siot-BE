package com.siot.backend.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "NTF-09 이메일 인증 코드 확인")
public record EmailVerificationConfirmRequest(
        @Schema(description = "메일로 받은 6자리 숫자", example = "123456")
        @NotBlank @Pattern(regexp = "^\\s*\\d{6}\\s*$") String code) {
}
