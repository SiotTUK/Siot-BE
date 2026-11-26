package com.siot.backend.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "알림 수신 설정 변경 요청")
public record NotificationPreferenceUpdateRequest(
        @NotBlank
        @Schema(description = "알림 종류 (STAY_EXPIRY, CHECKLIST_DEADLINE, TAX, EVENT, NOTICE_CHANGED)", example = "STAY_EXPIRY")
        String kind,

        @NotNull
        @Schema(description = "알림 수신 여부 (인앱 알림)", example = "true")
        Boolean enabled,

        @Schema(description = "사전 알림 일수", example = "30")
        Integer leadDays,

        @Schema(description = "NTF-05 이메일로도 받을지. 생략하면 그대로 (처음이면 true)", nullable = true, example = "false")
        Boolean emailEnabled
) {

    /** 이메일 수신 설정은 그대로 둔다 */
    public NotificationPreferenceUpdateRequest(String kind, Boolean enabled, Integer leadDays) {
        this(kind, enabled, leadDays, null);
    }
}
