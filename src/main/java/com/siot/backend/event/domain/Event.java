package com.siot.backend.event.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.LocalTime;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "event")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long id;

    @Column(name = "external_source", length = 100)
    private String externalSource;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "region_code", length = 20)
    private String regionCode;

    @Column(name = "starts_on")
    private LocalDate startsOn;

    @Column(name = "ends_on")
    private LocalDate endsOn;

    @Column(name = "starts_at")
    private LocalTime startsAt;

    @Column(name = "ends_at")
    private LocalTime endsAt;

    @Column(name = "application_deadline")
    private OffsetDateTime applicationDeadline;

    @Column(name = "time_is_unknown", nullable = false)
    private boolean timeUnknown = false;

    @Column(name = "is_multicultural", nullable = false)
    private boolean multicultural = false;

    @Column(name = "source_url", columnDefinition = "text")
    private String sourceUrl;

    @Column(name = "price_info", columnDefinition = "text")
    private String priceInfo;

    @Column(name = "application_url", columnDefinition = "text")
    private String applicationUrl;

    // 관리자가 수정한 값 보호 플래그 (재수집 시 덮어쓰기 방지)
    @Column(name = "starts_on_locked", nullable = false)
    private boolean startsOnLocked = false;

    @Column(name = "ends_on_locked", nullable = false)
    private boolean endsOnLocked = false;

    @Column(name = "starts_at_locked", nullable = false)
    private boolean startsAtLocked = false;

    @Column(name = "ends_at_locked", nullable = false)
    private boolean endsAtLocked = false;

    @Column(name = "deadline_locked", nullable = false)
    private boolean deadlineLocked = false;

    @Column(name = "price_info_locked", nullable = false)
    private boolean priceInfoLocked = false;

    @Column(name = "application_url_locked", nullable = false)
    private boolean applicationUrlLocked = false;

    @Column(nullable = false)
    private int revision = 1;

    @Column(nullable = false, length = 30)
    private String status = "ACTIVE";

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
}
