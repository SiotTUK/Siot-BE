package com.siot.backend.checklist.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

/**
 * 사용자가 표시한 항목 상태 (done[] / notApplicable[]). 회차(periodKey)별로 저장한다.
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

    /**
     * 회차. '' = 한 번만 하는 항목, YYYY-MM = 세금 납부 회차(CL-10·11),
     * YYYY-MM-DD = 생활 변화 기준일(같은 변화를 다시 등록하면 새 기준일의 할 일은 다시 TODO)
     */
    @Id
    @Column(name = "period_key", length = 10)
    private String periodKey = ONCE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProgressStatus status;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** 한 번만 하는 항목의 회차 키 */
    public static final String ONCE = "";

    public static ChecklistProgress of(Long userId, Long guideId, String periodKey, ProgressStatus status) {
        ChecklistProgress progress = new ChecklistProgress();
        progress.userId = userId;
        progress.guideId = guideId;
        progress.periodKey = periodKey == null ? ONCE : periodKey;
        progress.status = status;
        return progress;
    }

    public void changeStatus(ProgressStatus status) {
        this.status = status;
    }
}
