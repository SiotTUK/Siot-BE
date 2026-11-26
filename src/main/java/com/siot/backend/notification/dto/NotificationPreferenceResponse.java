package com.siot.backend.notification.dto;

import com.siot.backend.notification.domain.NotificationPreference;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "알림 수신 설정 응답")
public record NotificationPreferenceResponse(
        @Schema(description = "알림 종류 (STAY_EXPIRY, CHECKLIST_DEADLINE, TAX, EVENT, NOTICE_CHANGED)", example = "STAY_EXPIRY")
        String kind,
        @Schema(description = "알림 수신 여부 (인앱 알림)", example = "true")
        boolean enabled,
        @Schema(description = "사전 알림 일수 (D-Day 기준)", example = "30")
        int leadDays,
        @Schema(description = "NTF-05 이메일로도 받을지. 인증된 가입 이메일이 있어야 실제로 보낸다. enabled=false 면 이메일도 가지 않는다",
                example = "true")
        boolean emailEnabled
) {

    /** 이메일 수신은 기본으로 켜져 있다 */
    public NotificationPreferenceResponse(String kind, boolean enabled, int leadDays) {
        this(kind, enabled, leadDays, true);
    }

    public static NotificationPreferenceResponse from(NotificationPreference pref) {
        return new NotificationPreferenceResponse(
                pref.getKind(),
                pref.isEnabled(),
                pref.getLeadDays(),
                pref.isEmailEnabled()
        );
    }
}
