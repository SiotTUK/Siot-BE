package com.siot.backend.notification.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 30)
    private String kind;

    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "guide_id")
    private Long guideId;

    @Column(name = "schedule_id")
    private Long scheduleId;

    @Column(name = "document_version_id")
    private Long documentVersionId;

    @Column(name = "source_key")
    private String sourceKey;

    @Column(name = "source_revision")
    private Integer sourceRevision;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Column(nullable = false, length = 30)
    private String status = "PENDING";

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(nullable = false, length = 10)
    private String locale = "ko";

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    // NTF-05: 이메일 채널 발송 결과 (null 이면 이메일 발송 대상 아님)
    @Enumerated(EnumType.STRING)
    @Column(name = "email_status", length = 30)
    private EmailDeliveryStatus emailStatus;

    @Column(name = "email_attempts", nullable = false)
    private int emailAttempts = 0;

    @Column(name = "email_last_error", columnDefinition = "text")
    private String emailLastError;

    @Column(name = "email_sent_at")
    private LocalDateTime emailSentAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
