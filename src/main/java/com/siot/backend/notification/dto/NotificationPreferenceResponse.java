package com.siot.backend.notification.dto;

import com.siot.backend.notification.domain.NotificationLeadDays;
import com.siot.backend.notification.domain.NotificationPreference;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "알림 수신 설정 응답")
public record NotificationPreferenceResponse(
        @Schema(description = "알림 종류 (STAY_EXPIRY, CHECKLIST_DEADLINE, TAX, EVENT, NOTICE_CHANGED)", example = "STAY_EXPIRY")
        String kind,
        @Schema(description = "알림 수신 여부 (인앱 알림)", example = "true")
        boolean enabled,
        @Schema(description = "예전 사전 알림 일수 (하나). 발송에는 leadDaysList 를 쓴다", example = "30", deprecated = true)
        int leadDays,
        @Schema(description = "NTF-05 이메일로도 받을지. 인증된 가입 이메일이 있어야 실제로 보낸다. enabled=false 면 이메일도 가지 않는다",
                example = "true")
        boolean emailEnabled,
        @Schema(description = "NTF-03 실제로 받는 사전 알림 시점 D-n (큰 것부터). 고른 적 없으면 availableLeadDays 전부. "
                + "행사(EVENT)는 신청마다 고르므로, 안내 변경(NOTICE_CHANGED)은 시점이 없으므로 빈 배열", example = "[7, 1]")
        List<Integer> leadDaysList,
        @Schema(description = "이 종류에서 고를 수 있는 시점 D-n. STAY_EXPIRY: 120·60·30·14·7·1, CHECKLIST_DEADLINE·TAX: 7·1",
                example = "[7, 1]")
        List<Integer> availableLeadDays
) {

    /** 저장된 설정이 없을 때의 기본값: 이메일 받음, 시점 전부 */
    public static NotificationPreferenceResponse defaults(String kind, int defaultLeadDays) {
        List<Integer> available = NotificationLeadDays.available(kind);
        return new NotificationPreferenceResponse(kind, true, defaultLeadDays, true, available, available);
    }

    public static NotificationPreferenceResponse from(NotificationPreference pref) {
        return new NotificationPreferenceResponse(
                pref.getKind(),
                pref.isEnabled(),
                pref.getLeadDays(),
                pref.isEmailEnabled(),
                NotificationLeadDays.effective(pref.getKind(), pref.getLeadDaysList()),
                NotificationLeadDays.available(pref.getKind())
        );
    }
}
