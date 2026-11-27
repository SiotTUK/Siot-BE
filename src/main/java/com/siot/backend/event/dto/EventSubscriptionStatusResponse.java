package com.siot.backend.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 행사 알림 신청 여부. 응답 모양은 Map 이던 때와 같다: {@code {"subscribed": true, "leadDays": 1}} (BE #22) */
@Schema(description = "행사 알림 신청 여부")
public record EventSubscriptionStatusResponse(
        @Schema(description = "알림을 신청했으면 true", example = "true") boolean subscribed,
        @Schema(description = "신청한 알림 시점 (행사 시작 며칠 전: 1, 3, 7). 신청 안 했으면 null",
                nullable = true, allowableValues = {"1", "3", "7"}, example = "1") Integer leadDays) {

    public static EventSubscriptionStatusResponse of(Integer leadDays) {
        return new EventSubscriptionStatusResponse(leadDays != null, leadDays);
    }
}
