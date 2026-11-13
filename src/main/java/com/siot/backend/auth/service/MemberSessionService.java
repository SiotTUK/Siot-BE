package com.siot.backend.auth.service;

import com.siot.backend.member.event.MemberPasswordResetEvent;
import com.siot.backend.member.event.MemberWithdrawnEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 회원 한 명의 모든 세션(다른 기기 포함)을 삭제한다.
 * M-07 탈퇴, M-11 비밀번호 재설정에서 사용한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberSessionService {

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    public int invalidateAll(String loginId) {
        var sessionIds = sessionRepository.findByPrincipalName(loginId).keySet();
        sessionIds.forEach(sessionRepository::deleteById);
        return sessionIds.size();
    }

    /** 탈퇴가 DB에 커밋된 뒤에만 세션을 지운다 (삭제 실패 시 로그인 상태 유지) */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMemberWithdrawn(MemberWithdrawnEvent event) {
        int count = invalidateAll(event.loginId());
        log.info("Member {} withdrawn, {} session(s) invalidated", event.memberId(), count);
    }

    /** M-11: 비밀번호 재설정이 커밋된 뒤 모든 세션 삭제 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordReset(MemberPasswordResetEvent event) {
        int count = invalidateAll(event.loginId());
        log.info("Member {} password reset, {} session(s) invalidated", event.memberId(), count);
    }
}
