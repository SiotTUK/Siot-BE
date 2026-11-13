package com.siot.backend.member.dto;

/**
 * @param expiresInSeconds        인증 코드 유효 시간
 * @param resendAvailableInSeconds 다시 요청할 수 있을 때까지 남은 시간
 */
public record EmailVerificationResponse(String email, long expiresInSeconds, long resendAvailableInSeconds) {
}
