package com.siot.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(
        @NotBlank @Size(max = 100) String token,
        @NotNull String password,
        @NotNull String passwordConfirm
) {
}
