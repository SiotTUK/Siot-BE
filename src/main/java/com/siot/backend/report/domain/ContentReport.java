package com.siot.backend.report.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "content_report")
public class ContentReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Long id;

    @Column(name = "reporter_user_id")
    private Long reporterUserId;

    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "guide_id")
    private Long guideId;

    @Column(name = "target_title_snapshot")
    private String targetTitleSnapshot;

    @Column(name = "quoted_text", columnDefinition = "text")
    private String quotedText;

    /** COM-01: 신고 당시 화면 언어 */
    @Column(nullable = false, length = 10)
    private String language = "ko";

    @Column(nullable = false, length = 50)
    private String reason;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false, length = 30)
    private String status = "RECEIVED";

    @Column(name = "admin_memo", columnDefinition = "text")
    private String adminMemo;

    @Column(name = "processed_by")
    private Long processedBy;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
