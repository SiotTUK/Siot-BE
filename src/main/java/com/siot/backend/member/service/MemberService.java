package com.siot.backend.member.service;

import com.siot.backend.global.exception.BusinessException;
import com.siot.backend.global.exception.ErrorCode;
import com.siot.backend.member.domain.AppUser;
import com.siot.backend.member.dto.MemberResponse;
import com.siot.backend.member.dto.MemberUpdateRequest;
import com.siot.backend.member.dto.OnboardingRequest;
import com.siot.backend.member.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final AppUserRepository appUserRepository;

    @Transactional(readOnly = true)
    public MemberResponse getMember(Long memberId) {
        return MemberResponse.from(findMember(memberId));
    }

    /** 내 정보 일부 수정. null 항목은 그대로 둔다 */
    @Transactional
    public MemberResponse update(Long memberId, MemberUpdateRequest request) {
        AppUser user = findMember(memberId);
        if (request.language() != null) {
            user.changeLanguage(request.language());
        }
        return MemberResponse.from(user);
    }

    /**
     * 온보딩 답 저장 (처음 온보딩·내 정보 화면 수정 공통). 5개 답 전체를 덮어쓴다.
     * 체크리스트 완료 기록은 지우지 않는다 (AppUser#updateOnboarding).
     */
    @Transactional
    public MemberResponse saveOnboarding(Long memberId, OnboardingRequest request) {
        AppUser user = findMember(memberId);
        user.updateOnboarding(request.userType(), request.entryDate(), request.purpose(),
                request.residences(), request.stayExpiry());
        return MemberResponse.from(user);
    }

    private AppUser findMember(Long memberId) {
        return appUserRepository.findById(memberId)
                // 세션은 남아 있지만 회원이 삭제된 경우
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }
}
