package com.siot.backend.member.service;

import com.siot.backend.global.exception.BusinessException;
import com.siot.backend.global.exception.ErrorCode;
import com.siot.backend.member.domain.AppUser;
import com.siot.backend.member.event.MemberWithdrawnEvent;
import com.siot.backend.member.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * M-06 / M-07: 회원 탈퇴.
 * <p>
 * app_user 행을 물리 삭제하면 DB FK 규칙(V2__init_schema.sql)에 따라 한 트랜잭션 안에서 함께 정리된다.
 * <ul>
 *   <li>CASCADE (삭제): 체크리스트 완료 기록, 행사 알림 신청, 알림 설정, 알림, 대화 기록, refresh_token</li>
 *   <li>SET NULL (작성자만 제거): 오류 신고, 관리자 작성·수정 이력, 챗봇 검토자</li>
 *   <li>app_user 컬럼: 생활 정보(입국일 등), 알림 이메일</li>
 * </ul>
 * 세션 삭제는 커밋 이후 {@link MemberWithdrawnEvent} 로 처리한다.
 */
@Service
@RequiredArgsConstructor
public class MemberWithdrawalService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void withdraw(Long memberId, String password) {
        AppUser user = appUserRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PASSWORD_INCORRECT);
        }

        appUserRepository.delete(user);
        appUserRepository.flush();   // FK 위반 등 삭제 실패를 이 트랜잭션 안에서 드러낸다

        eventPublisher.publishEvent(new MemberWithdrawnEvent(user.getId(), user.getLoginId()));
    }
}
