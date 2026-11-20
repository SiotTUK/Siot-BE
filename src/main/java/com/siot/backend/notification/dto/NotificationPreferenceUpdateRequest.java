package com.siot.backend.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "알림 수신 설정 변경 요청")
public record NotificationPreferenceUpdateRequest(
        @NotBlank
        @Schema(description = "알림 종류 (STAY_EXPIRY, TAX, EVENT, NOTICE_CHANGED 등)", example = "STAY_EXPIRY")
        String kind,

        @NotNull
        @Schema(description = "알림 수신 여부", example = "true")
        Boolean enabled,

        @Schema(description = "사전 알림 일수", example = "30")
        Integer leadDays
) {
}
