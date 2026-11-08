package com.siot.backend.member.dto;

import com.siot.backend.member.domain.AppUser;
import com.siot.backend.member.domain.Role;

public record MemberResponse(
        Long id,
        String loginId,
        Role role,
        String language,
        String userType,
        boolean emailRegistered,
        boolean emailVerified
) {

    public static MemberResponse from(AppUser user) {
        return new MemberResponse(
                user.getId(),
                user.getLoginId(),
                user.getRole(),
                user.getLanguage(),
                user.getUserType(),
                user.getEmail() != null,
                user.getEmailVerifiedAt() != null);
    }
}
