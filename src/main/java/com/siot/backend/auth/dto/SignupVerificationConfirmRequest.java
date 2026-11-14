package com.siot.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupVerificationConfirmRequest(
        @NotBlank @Size(max = 255) String email,
        @NotBlank @Pattern(regexp = "^\\s*\\d{6}\\s*$") String code
) {
}
