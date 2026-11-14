package com.siot.backend.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.siot.backend.member.domain.AppUser;
import com.siot.backend.member.domain.Role;

import java.time.OffsetDateTime;

/** ADM-01: 관리자 화면의 회원 목록 */
@Schema(description = "ADM-01 관리자 화면의 회원")
public record AdminMemberResponse(
        @Schema(description = "회원 ID", example = "1") Long id,
        @Schema(description = "가입 이메일", example = "user@example.com") String email,
        @Schema(description = "권한", example = "USER") Role role,
        @Schema(description = "가입 시각") OffsetDateTime createdAt) {

    public static AdminMemberResponse from(AppUser user) {
        return new AdminMemberResponse(user.getId(), user.getEmail(), user.getRole(), user.getCreatedAt());
    }
}
