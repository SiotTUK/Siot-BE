package com.siot.backend.notification.dto;

import com.siot.backend.checklist.domain.Guide;
import com.siot.backend.notification.domain.EmailDeliveryStatus;
import com.siot.backend.notification.domain.Notification;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "알림 응답")
public record NotificationResponse(
        @Schema(description = "알림 ID", example = "1")
        Long notificationId,
        @Schema(description = "알림 종류 (STAY_EXPIRY, REGISTRATION, RESIDENCE_REPORT, TAX, EVENT, NOTICE_CHANGED)",
                example = "STAY_EXPIRY")
        String kind,
        @Schema(description = "관련 행사 ID", nullable = true)
        Long eventId,
        @Schema(description = "관련 항목 ID", nullable = true)
        Long guideId,
        @Schema(description = "관련 생활 달력 일정 ID", nullable = true)
        Long scheduleId,
        @Schema(description = "관련 문서 버전 ID", nullable = true)
        Long documentVersionId,
        @Schema(description = "NTF-04 이동할 화면 종류: EVENT(행사 상세) / CHECKLIST·LIFE_CHANGE_TASK·RULE(항목 상세) / "
                + "SCHEDULE(세금 상세) / CALENDAR(생활 달력). 프론트엔드는 linkType·linkCode 로 경로를 만든다 (챗봇 관련 페이지 CB-10 과 같은 방식)",
                example = "CHECKLIST")
        String linkType,
        @Schema(description = "NTF-04 이동할 대상: 행사는 eventId, 항목은 code(CL-01 …), 세금 상세는 scheduleId. CALENDAR 는 null",
                nullable = true, example = "CL-01")
        String linkCode,
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

    public static final String LINK_EVENT = "EVENT";
    public static final String LINK_SCHEDULE = "SCHEDULE";
    public static final String LINK_CALENDAR = "CALENDAR";

    /**
     * @param guide 알림의 guideId 항목 (없거나 지워진 행이면 null). 항목 상세로 가려면 code 가 필요하다
     */
    public static NotificationResponse from(Notification n, Guide guide) {
        String linkType;
        String linkCode;
        if (n.getEventId() != null) {
            linkType = LINK_EVENT;
            linkCode = n.getEventId().toString();
        } else if (guide != null) {
            linkType = guide.getKind();
            linkCode = guide.getCode();
        } else if (n.getScheduleId() != null) {
            linkType = LINK_SCHEDULE;
            linkCode = n.getScheduleId().toString();
        } else {
            linkType = LINK_CALENDAR; // 체류 만료 등 개인 일정
            linkCode = null;
        }
        return new NotificationResponse(
                n.getId(),
                n.getKind(),
                n.getEventId(),
                n.getGuideId(),
                n.getScheduleId(),
                n.getDocumentVersionId(),
                linkType,
                linkCode,
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
