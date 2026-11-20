package com.siot.backend.notification.dto;

import com.siot.backend.notification.domain.NotificationPreference;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "알림 수신 설정 응답")
public record NotificationPreferenceResponse(
        @Schema(description = "알림 종류 (STAY_EXPIRY, CHECKLIST_DEADLINE, TAX, EVENT, NOTICE_CHANGED)", example = "STAY_EXPIRY")
        String kind,
        @Schema(description = "알림 수신 여부", example = "true")
        boolean enabled,
        @Schema(description = "사전 알림 일수 (D-Day 기준)", example = "30")
        int leadDays
) {
    public static NotificationPreferenceResponse from(NotificationPreference pref) {
        return new NotificationPreferenceResponse(
                pref.getKind(),
                pref.isEnabled(),
                pref.getLeadDays()
        );
    }
}
