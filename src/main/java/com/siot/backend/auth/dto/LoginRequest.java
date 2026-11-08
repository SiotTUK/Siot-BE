package com.siot.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 20) String loginId,
        @NotBlank @Size(max = 64) String password
) {
}
