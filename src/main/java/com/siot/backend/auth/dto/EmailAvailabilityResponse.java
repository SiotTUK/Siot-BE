package com.siot.backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "M-02 이메일 중복 확인 결과")
public record EmailAvailabilityResponse(
        @Schema(description = "확인한 이메일 (앞뒤 공백 제거, 소문자)", example = "user@example.com") String email,
        @Schema(description = "true = 가입할 수 있음, false = 이미 가입한 이메일", example = "true") boolean available) {
}
