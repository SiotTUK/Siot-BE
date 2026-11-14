package com.siot.backend.member.service;

import com.siot.backend.auth.domain.CredentialPolicy;
import com.siot.backend.global.exception.BusinessException;
import com.siot.backend.global.exception.ErrorCode;
import com.siot.backend.member.domain.AppUser;
import com.siot.backend.member.domain.Role;
import com.siot.backend.member.dto.AdminMemberResponse;
import com.siot.backend.member.event.MemberRoleChangedEvent;
import com.siot.backend.member.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** ADM-01: 관리자 권한 관리 */
@Service
@RequiredArgsConstructor
public class AdminMemberService {

    private final AppUserRepository appUserRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** 이메일 일부로 검색, 권한으로 거르기 (둘 다 선택). 가입 최신순 */
    @Transactional(readOnly = true)
    public Page<AdminMemberResponse> search(String email, Role role, Pageable pageable) {
        String keyword = email == null || email.isBlank() ? "" : CredentialPolicy.normalizeEmail(email);
        var page = role == null
                ? appUserRepository.searchForAdmin(keyword, pageable)
                : appUserRepository.searchForAdmin(keyword, role, pageable);
        return page.map(AdminMemberResponse::from);
    }

    @Transactional
    public AdminMemberResponse changeRole(Long adminId, Long memberId, Role role) {
        // 관리자가 아무도 남지 않는 일을 막기 위해 자기 자신의 관리자 권한은 내릴 수 없다
        if (adminId.equals(memberId) && role != Role.ADMIN) {
            throw new BusinessException(ErrorCode.ADMIN_SELF_DEMOTION);
        }
        AppUser user = appUserRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (user.getRole() != role) {
            user.changeRole(role);
            eventPublisher.publishEvent(new MemberRoleChangedEvent(user.getId(), user.getEmail()));
        }
        return AdminMemberResponse.from(user);
    }
}
