package com.siot.backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "M-11 새 비밀번호 설정")
public record PasswordResetConfirmRequest(
        @Schema(description = "메일 링크의 token 값 (30분 유효, 1회용)")
        @NotBlank @Size(max = 100) String token,
        @Schema(description = "새 비밀번호. 8~64자, 영문·숫자 각 1자 이상, 영문·숫자·특수문자만, 공백 불가, 이메일과 같으면 안 됨", example = "newpass5678")
        @NotNull String password,
        @Schema(description = "새 비밀번호 확인 (password 와 같아야 함)", example = "newpass5678")
        @NotNull String passwordConfirm
) {
}
