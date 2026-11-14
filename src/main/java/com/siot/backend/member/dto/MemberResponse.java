package com.siot.backend.member.dto;

import com.siot.backend.member.domain.AppUser;
import com.siot.backend.member.domain.Role;
import com.siot.backend.member.domain.UserType;

public record MemberResponse(
        Long id,
        String email,
        Role role,
        String language,
        UserType userType,
        /** 가입 이메일 인증 여부 (인증해야 비밀번호 재설정·이메일 알림을 받는다) */
        boolean emailVerified,
        /** false 면 가입 직후 온보딩(사용자 유형)으로 이동 */
        boolean onboarded
) {

    public static MemberResponse from(AppUser user) {
        return new MemberResponse(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.getLanguage(),
                user.getUserType(),
                user.hasVerifiedEmail(),
                user.isOnboarded());
    }
}
