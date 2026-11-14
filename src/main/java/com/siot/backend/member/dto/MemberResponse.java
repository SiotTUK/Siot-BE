package com.siot.backend.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.siot.backend.member.domain.AppUser;
import com.siot.backend.member.domain.Role;
import com.siot.backend.member.domain.UserType;

@Schema(description = "회원 정보 (로그인·가입·내 정보 응답)")
public record MemberResponse(
        @Schema(description = "회원 ID", example = "1") Long id,
        @Schema(description = "가입 이메일 (= 아이디)", example = "user@example.com") String email,
        @Schema(description = "권한. ADMIN 이면 관리자 화면 사용 가능", example = "USER") Role role,
        @Schema(description = "화면 언어", allowableValues = {"ko", "en", "zh", "vi"}, example = "ko") String language,
        @Schema(description = "사용자 유형 (F02). NEW = 처음 입국, LIVING = 원래 거주, null = 온보딩 전", nullable = true, example = "NEW") UserType userType,
        /** 가입 이메일 인증 여부 (인증해야 비밀번호 재설정·이메일 알림을 받는다) */
        @Schema(description = "가입 이메일 인증 여부. false 면 비밀번호 재설정·이메일 알림을 받지 못한다 (NTF-09 로 인증)", example = "true")
        boolean emailVerified,
        /** false 면 가입 직후 온보딩(사용자 유형)으로 이동 */
        @Schema(description = "false 면 온보딩(사용자 유형) 화면으로 이동", example = "false")
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
