package com.siot.backend.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record EmailVerificationConfirmRequest(@NotBlank @Pattern(regexp = "^\\s*\\d{6}\\s*$") String code) {
}
