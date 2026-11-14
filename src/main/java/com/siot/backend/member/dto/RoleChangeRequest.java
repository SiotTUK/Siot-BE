package com.siot.backend.member.dto;

import com.siot.backend.member.domain.Role;
import jakarta.validation.constraints.NotNull;

public record RoleChangeRequest(@NotNull Role role) {
}
