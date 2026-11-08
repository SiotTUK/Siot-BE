package com.siot.backend.member.event;

/**
 * 회원 탈퇴가 DB에 커밋된 뒤 처리할 작업(세션 삭제 등)을 위한 이벤트.
 * 다른 기능에서 탈퇴 후 정리가 필요하면 {@code @TransactionalEventListener} 로 구독한다.
 */
public record MemberWithdrawnEvent(Long memberId, String loginId) {
}
