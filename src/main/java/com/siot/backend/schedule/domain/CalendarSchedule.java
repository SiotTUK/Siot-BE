package com.siot.backend.schedule.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "calendar_schedule")
public class CalendarSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "schedule_id")
    private Long id;

    @Column(name = "guide_id")
    private Long guideId;

    @Column(nullable = false, length = 30)
    private String kind;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on")
    private LocalDate endsOn;

    @Column(name = "target_description", columnDefinition = "text")
    private String targetDescription;

    @Column(name = "source_url", columnDefinition = "text")
    private String sourceUrl;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static CalendarSchedule create(Long guideId, String kind, LocalDate startsOn, LocalDate endsOn,
                                          String targetDescription, String sourceUrl, boolean active, Long createdBy) {
        CalendarSchedule schedule = new CalendarSchedule();
        schedule.guideId = guideId;
        schedule.kind = kind;
        schedule.startsOn = startsOn;
        schedule.endsOn = endsOn;
        schedule.targetDescription = targetDescription;
        schedule.sourceUrl = sourceUrl;
        schedule.active = active;
        schedule.createdBy = createdBy;
        return schedule;
    }

    public void update(LocalDate startsOn, LocalDate endsOn, String targetDescription,
                       String sourceUrl, boolean active, Long updatedBy) {
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.targetDescription = targetDescription;
        this.sourceUrl = sourceUrl;
        this.active = active;
        this.updatedBy = updatedBy;
    }
}
