package com.siot.backend.member.event;

/** M-11: 비밀번호 재설정이 커밋된 뒤 그 회원의 모든 세션을 삭제하기 위한 이벤트 */
public record MemberPasswordResetEvent(Long memberId, String email) {
}
