package com.siot.backend.checklist.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "checklist_progress")
@IdClass(ChecklistProgressId.class)
public class ChecklistProgress {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "guide_id")
    private Long guideId;

    @Column(name = "is_selected", nullable = false)
    private boolean selected = true;

    @Column(name = "is_completed", nullable = false)
    private boolean completed = false;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
