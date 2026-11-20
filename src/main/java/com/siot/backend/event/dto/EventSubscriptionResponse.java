package com.siot.backend.event.dto;

import com.siot.backend.event.domain.EventSubscription;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "행사 알림 신청(구독) 응답")
public record EventSubscriptionResponse(
        @Schema(description = "행사 ID", example = "1")
        Long eventId,

        @Schema(description = "회원 ID", example = "10")
        Long userId,

        @Schema(description = "알림 신청 여부", example = "true")
        boolean enabled,

        @Schema(description = "신청/변경 시각")
        OffsetDateTime updatedAt
) {
    public static EventSubscriptionResponse from(EventSubscription sub) {
        return new EventSubscriptionResponse(
                sub.getEventId(),
                sub.getUserId(),
                sub.isEnabled(),
                sub.getUpdatedAt()
        );
    }
}
