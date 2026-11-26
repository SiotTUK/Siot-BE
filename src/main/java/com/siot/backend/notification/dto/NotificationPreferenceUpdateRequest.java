package com.siot.backend.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "알림 수신 설정 변경 요청")
public record NotificationPreferenceUpdateRequest(
        @NotBlank
        @Schema(description = "알림 종류 (STAY_EXPIRY, CHECKLIST_DEADLINE, TAX, EVENT, NOTICE_CHANGED)", example = "STAY_EXPIRY")
        String kind,

        @NotNull
        @Schema(description = "알림 수신 여부 (인앱 알림)", example = "true")
        Boolean enabled,

        @Schema(description = "예전 사전 알림 일수 (하나). 저장만 하고 발송에는 쓰지 않는다. 시점은 leadDaysList 로 고른다",
                example = "30", deprecated = true)
        Integer leadDays,

        @Schema(description = "NTF-05 이메일로도 받을지. 생략하면 그대로 (처음이면 true)", nullable = true, example = "false")
        Boolean emailEnabled,

        @Schema(description = "NTF-03 받을 사전 알림 시점 D-n (하나 이상, availableLeadDays 중에서). 예: D-7만 [7], D-7·D-1 [7, 1]. "
                + "생략하면 그대로. 모두 끄려면 enabled=false", nullable = true, example = "[7, 1]")
        List<Integer> leadDaysList
) {

    /** 이메일 수신·시점은 그대로 둔다 */
    public NotificationPreferenceUpdateRequest(String kind, Boolean enabled, Integer leadDays) {
        this(kind, enabled, leadDays, null, null);
    }
}
