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
import java.util.Objects;

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

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DELETED = "DELETED";

    /** 수집한 새 행사 (EVE-01). 일정·내용은 {@link #collectSchedule}, {@link #collectDetails} 로 채운다 */
    public static Event collected(String externalSource, String externalId) {
        Event event = new Event();
        event.externalSource = externalSource;
        event.externalId = externalId;
        return event;
    }

    /** 관리자가 지운 행사. 다시 수집해도 살리거나 고치지 않는다 */
    public boolean isDeleted() {
        return STATUS_DELETED.equals(status);
    }

    /**
     * 수집한 일정을 반영한다. 잠긴 칸(관리자 수정)은 그대로 둔다.
     * 수집 값이 null 이면 기존 값을 지우지 않는다 (시작일·종료일 중 하나는 늘 있다).
     * 시각을 모르면 시작·종료 시각을 비우고 time_is_unknown.
     *
     * @return 바뀐 칸이 있으면 true
     */
    public boolean collectSchedule(LocalDate newStartsOn, LocalDate newEndsOn, LocalTime newStartsAt,
                                   LocalTime newEndsAt) {
        boolean changed = false;
        if (!startsOnLocked && newStartsOn != null && !newStartsOn.equals(startsOn)) {
            startsOn = newStartsOn;
            changed = true;
        }
        if (!endsOnLocked && newEndsOn != null && !newEndsOn.equals(endsOn)) {
            endsOn = newEndsOn;
            changed = true;
        }
        if (!startsAtLocked && !Objects.equals(newStartsAt, startsAt)) {
            startsAt = newStartsAt;
            changed = true;
        }
        if (!endsAtLocked && !Objects.equals(newEndsAt, endsAt)) {
            endsAt = newEndsAt;
            changed = true;
        }
        boolean unknown = startsAt == null && endsAt == null;
        if (unknown != timeUnknown) {
            timeUnknown = unknown;
            changed = true;
        }
        return changed;
    }

    /**
     * 수집한 지역·가격·원문 링크·다문화 여부를 반영한다. 잠긴 가격은 두고, null 인 수집 값은 기존 값을 지우지 않는다.
     *
     * @return 바뀐 칸이 있으면 true
     */
    public boolean collectDetails(String newRegionCode, String newPriceInfo, String newSourceUrl, boolean newMulticultural) {
        boolean changed = false;
        if (newRegionCode != null && !newRegionCode.equals(regionCode)) {
            regionCode = newRegionCode;
            changed = true;
        }
        if (!priceInfoLocked && newPriceInfo != null && !newPriceInfo.equals(priceInfo)) {
            priceInfo = newPriceInfo;
            changed = true;
        }
        if (newSourceUrl != null && !newSourceUrl.equals(sourceUrl)) {
            sourceUrl = newSourceUrl;
            changed = true;
        }
        if (newMulticultural != multicultural) {
            multicultural = newMulticultural;
            changed = true;
        }
        return changed;
    }

    /** 내용이 바뀌었다 (번역을 다시 만든다, COM-02) */
    public void increaseRevision() {
        revision++;
    }
}
