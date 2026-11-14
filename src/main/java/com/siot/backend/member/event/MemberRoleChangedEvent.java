package com.siot.backend.member.event;

/** ADM-01: 권한이 바뀐 뒤 그 회원의 세션을 모두 끊어 새 권한이 바로 적용되게 한다 */
public record MemberRoleChangedEvent(Long memberId, String email) {
}
