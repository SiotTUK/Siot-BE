package com.siot.backend.checklist.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 사용자가 표시한 항목 상태 (done[] / notApplicable[]).
 * 행이 없으면 TODO 또는 OVERDUE (계산). 온보딩 답을 바꿔도 지우지 않는다.
 */
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProgressStatus status;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static ChecklistProgress of(Long userId, Long guideId, ProgressStatus status) {
        ChecklistProgress progress = new ChecklistProgress();
        progress.userId = userId;
        progress.guideId = guideId;
        progress.status = status;
        return progress;
    }

    public void changeStatus(ProgressStatus status) {
        this.status = status;
    }
}
