package com.siot.backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @param expiresInSeconds         인증 코드 유효 시간
 * @param resendAvailableInSeconds 다시 요청할 수 있을 때까지 남은 시간
 */
@Schema(description = "가입 인증 코드 발송 결과")
public record SignupVerificationResponse(
        @Schema(description = "코드를 보낸 이메일 (소문자)", example = "user@example.com") String email,
        @Schema(description = "코드 유효 시간(초)", example = "600") long expiresInSeconds,
        @Schema(description = "다시 요청할 수 있을 때까지 남은 시간(초)", example = "60") long resendAvailableInSeconds) {
}
