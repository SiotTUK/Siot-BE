package com.siot.backend.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.siot.backend.member.domain.Role;
import jakarta.validation.constraints.NotNull;

@Schema(description = "ADM-01 권한 변경")
public record RoleChangeRequest(
        @Schema(description = "바꿀 권한", example = "ADMIN") @NotNull Role role) {
}
