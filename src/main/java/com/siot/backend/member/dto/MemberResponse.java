package com.siot.backend.member.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import com.siot.backend.member.domain.AppUser;
import com.siot.backend.member.domain.Purpose;
import com.siot.backend.member.domain.Residence;
import com.siot.backend.member.domain.Role;
import com.siot.backend.member.domain.UserType;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "회원 정보 (로그인·가입·내 정보·온보딩 저장 응답)")
public record MemberResponse(
        @Schema(description = "회원 ID", example = "1") Long id,
        @Schema(description = "가입 이메일 (= 아이디)", example = "user@example.com") String email,
        @Schema(description = "권한. ADMIN 이면 관리자 화면 사용 가능", example = "USER") Role role,
        @Schema(description = "화면 언어", allowableValues = {"ko", "en", "zh", "vi"}, example = "ko") String language,
        @Schema(description = "사용자 유형 (F02). NEW = 처음 입국, LIVING = 원래 거주, null = 온보딩 전", nullable = true, example = "NEW") UserType userType,
        @Schema(description = "F03 입국 날짜. null = 입력 안 함", nullable = true, example = "2026-11-01") LocalDate entryDate,
        @Schema(description = "F04 체류 목적. null = 건너뜀 (체크리스트는 OTHER 로 계산)", nullable = true, example = "STUDY") Purpose purpose,
        @ArraySchema(arraySchema = @Schema(description = "F05 거주 정보. 빈 배열 = 고르지 않음"),
                schema = @Schema(implementation = Residence.class))
        List<Residence> residences,
        @Schema(description = "F06 체류 만료일. null = 입력 안 함", nullable = true, example = "2027-11-01") LocalDate stayExpiry,
        /** 가입 이메일 인증 여부 (인증해야 비밀번호 재설정·이메일 알림을 받는다) */
        @Schema(description = "가입 이메일 인증 여부. false 면 비밀번호 재설정·이메일 알림을 받지 못한다 (NTF-09 로 인증)", example = "true")
        boolean emailVerified,
        /** false 면 가입 직후 온보딩(사용자 유형)으로 이동 */
        @Schema(description = "false 면 온보딩(사용자 유형) 화면으로 이동", example = "false")
        boolean onboarded
) {

    /** residences 는 지연 로딩이므로 트랜잭션 안에서 호출한다 */
    public static MemberResponse from(AppUser user) {
        return new MemberResponse(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.getLanguage(),
                user.getUserType(),
                user.getEntryDate(),
                user.getPurpose(),
                // enum 선언 순서로 고정해 응답이 매번 같게 한다
                user.getResidences().stream().sorted().toList(),
                user.getStayExpiry(),
                user.hasVerifiedEmail(),
                user.isOnboarded());
    }
}
