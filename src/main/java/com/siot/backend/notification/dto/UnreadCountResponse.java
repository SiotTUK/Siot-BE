package com.siot.backend.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 안 읽은 알림 개수. 응답 모양은 Map 이던 때와 같다: {@code {"unreadCount": n}} (BE #22) */
@Schema(description = "안 읽은 알림 개수")
public record UnreadCountResponse(
        @Schema(description = "안 읽은 알림 개수", example = "3") long unreadCount) {
}
