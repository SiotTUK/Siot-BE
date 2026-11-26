package com.siot.backend.event.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "event_subscription")
@IdClass(EventSubscriptionId.class)
public class EventSubscription {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "event_id")
    private Long eventId;

    @Column(nullable = false)
    private boolean enabled = true;

    /** NTF-02 마지막으로 안내한 행사 일정 ({@link Event#scheduleSnapshot()}). null = 아직 기록 전 */
    @Column(name = "schedule_snapshot", length = 100)
    private String scheduleSnapshot;

    /** NTF-02 행사 시작 며칠 전에 알릴지: 1(하루 전, 기본) / 3 / 7 ({@link #LEAD_DAYS_OPTIONS}) */
    @Column(name = "lead_days", nullable = false)
    private int leadDays = DEFAULT_LEAD_DAYS;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static final int DEFAULT_LEAD_DAYS = 1;
    public static final java.util.List<Integer> LEAD_DAYS_OPTIONS = java.util.List.of(1, 3, 7);

    /** 알림 시점 변경. null 이면 그대로 */
    public void changeLeadDays(Integer leadDays) {
        if (leadDays != null) {
            this.leadDays = leadDays;
        }
    }

    public static EventSubscription of(Long userId, Long eventId, boolean enabled) {
        EventSubscription subscription = new EventSubscription();
        subscription.userId = userId;
        subscription.eventId = eventId;
        subscription.enabled = enabled;
        return subscription;
    }

    public void enable() {
        this.enabled = true;
    }

    /** 신청하거나 변경을 안내한 시점의 일정을 기록한다 (NTF-02) */
    public void recordSchedule(String snapshot) {
        this.scheduleSnapshot = snapshot;
    }

    public void disable() {
        this.enabled = false;
    }
}
