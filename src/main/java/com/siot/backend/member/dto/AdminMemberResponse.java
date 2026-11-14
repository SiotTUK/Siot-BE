package com.siot.backend.member.dto;

import com.siot.backend.member.domain.AppUser;
import com.siot.backend.member.domain.Role;

import java.time.OffsetDateTime;

/** ADM-01: 관리자 화면의 회원 목록 */
public record AdminMemberResponse(Long id, String email, Role role, OffsetDateTime createdAt) {

    public static AdminMemberResponse from(AppUser user) {
        return new AdminMemberResponse(user.getId(), user.getEmail(), user.getRole(), user.getCreatedAt());
    }
}
