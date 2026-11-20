package com.siot.backend.notification.dto;

import com.siot.backend.notification.domain.EmailDeliveryStatus;
import com.siot.backend.notification.domain.Notification;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "알림 응답")
public record NotificationResponse(
        @Schema(description = "알림 ID", example = "1")
        Long notificationId,
        @Schema(description = "알림 종류", example = "STAY_EXPIRY")
        String kind,
        Long eventId,
        Long guideId,
        Long scheduleId,
        Long documentVersionId,
        @Schema(description = "알림 제목", example = "체류기간 만료 30일 전입니다.")
        String title,
        @Schema(description = "알림 본문")
        String body,
        @Schema(description = "알림 언어", example = "ko")
        String locale,
        @Schema(description = "인앱 알림 발송 상태", example = "SENT")
        String status,
        @Schema(description = "발송 예정 시각")
        OffsetDateTime scheduledAt,
        @Schema(description = "전달 완료 시각")
        OffsetDateTime deliveredAt,
        @Schema(description = "읽은 시각")
        OffsetDateTime readAt,
        @Schema(description = "읽음 여부")
        boolean read,
        @Schema(description = "이메일 발송 상태 (null이면 이메일 대상 아님)")
        EmailDeliveryStatus emailStatus,
        @Schema(description = "생성 시각")
        OffsetDateTime createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getKind(),
                n.getEventId(),
                n.getGuideId(),
                n.getScheduleId(),
                n.getDocumentVersionId(),
                n.getTitle(),
                n.getBody(),
                n.getLocale(),
                n.getStatus(),
                n.getScheduledAt(),
                n.getDeliveredAt(),
                n.getReadAt(),
                n.getReadAt() != null,
                n.getEmailStatus(),
                n.getCreatedAt()
        );
    }
}
