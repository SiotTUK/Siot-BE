package com.siot.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** M-11: 비밀번호 찾기에서 이메일(아이디) 입력 */
public record PasswordResetRequest(@NotBlank @Size(max = 255) String email) {
}
