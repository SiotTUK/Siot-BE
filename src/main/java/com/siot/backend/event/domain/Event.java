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
import java.util.function.Consumer;

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

    @Column(name = "region_code_locked", nullable = false)
    private boolean regionCodeLocked = false;

    @Column(name = "source_url_locked", nullable = false)
    private boolean sourceUrlLocked = false;

    @Column(name = "multicultural_locked", nullable = false)
    private boolean multiculturalLocked = false;

    /** EVE-10 카테고리 ({@link EventCategory}) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventCategory category = EventCategory.EVENT;

    @Column(name = "category_locked", nullable = false)
    private boolean categoryLocked = false;

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
    /** 관리자가 직접 등록한 행사의 external_source (ADM-05, EVE-07). 수집 출처({@code EventSourceType})가 아니다 */
    public static final String SOURCE_MANUAL = "MANUAL";

    /** 수집한 새 행사 (EVE-01). 일정·내용은 {@link #collectSchedule}, {@link #collectDetails} 로 채운다 */
    public static Event collected(String externalSource, String externalId) {
        Event event = new Event();
        event.externalSource = externalSource;
        event.externalId = externalId;
        return event;
    }

    /** 관리자가 직접 등록한 행사 (EVE-07). 내용은 edit* 로 채우고, 잠금은 걸지 않는다 (수집 대상이 아니다) */
    public static Event manual(Long adminId) {
        Event event = new Event();
        event.externalSource = SOURCE_MANUAL;
        event.createdBy = adminId;
        event.updatedBy = adminId;
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
        if (!regionCodeLocked && newRegionCode != null && !newRegionCode.equals(regionCode)) {
            regionCode = newRegionCode;
            changed = true;
        }
        if (!priceInfoLocked && newPriceInfo != null && !newPriceInfo.equals(priceInfo)) {
            priceInfo = newPriceInfo;
            changed = true;
        }
        if (!sourceUrlLocked && newSourceUrl != null && !newSourceUrl.equals(sourceUrl)) {
            sourceUrl = newSourceUrl;
            changed = true;
        }
        if (!multiculturalLocked && newMulticultural != multicultural) {
            multicultural = newMulticultural;
            changed = true;
        }
        return changed;
    }

    // ---------------------------------------------------------------- 관리자 등록·수정 (ADM-05, EVE-07·08)
    // edit* 는 값을 바꾸고 lock=true 면 그 칸을 잠근다 (수집이 덮어쓰지 않게). 바뀌었으면 true

    public boolean editStartsOn(LocalDate value, boolean lock) {
        startsOnLocked |= lock;
        return set(startsOn, value, v -> startsOn = v);
    }

    public boolean editEndsOn(LocalDate value, boolean lock) {
        endsOnLocked |= lock;
        return set(endsOn, value, v -> endsOn = v);
    }

    /** 시작·종료 시각을 바꾸면 time_is_unknown 도 다시 정한다 */
    public boolean editStartsAt(LocalTime value, boolean lock) {
        startsAtLocked |= lock;
        boolean changed = set(startsAt, value, v -> startsAt = v);
        timeUnknown = startsAt == null && endsAt == null;
        return changed;
    }

    public boolean editEndsAt(LocalTime value, boolean lock) {
        endsAtLocked |= lock;
        boolean changed = set(endsAt, value, v -> endsAt = v);
        timeUnknown = startsAt == null && endsAt == null;
        return changed;
    }

    public boolean editApplicationDeadline(OffsetDateTime value, boolean lock) {
        deadlineLocked |= lock;
        return set(applicationDeadline, value, v -> applicationDeadline = v);
    }

    public boolean editPriceInfo(String value, boolean lock) {
        priceInfoLocked |= lock;
        return set(priceInfo, value, v -> priceInfo = v);
    }

    public boolean editApplicationUrl(String value, boolean lock) {
        applicationUrlLocked |= lock;
        return set(applicationUrl, value, v -> applicationUrl = v);
    }

    public boolean editSourceUrl(String value, boolean lock) {
        sourceUrlLocked |= lock;
        return set(sourceUrl, value, v -> sourceUrl = v);
    }

    public boolean editRegionCode(String value, boolean lock) {
        regionCodeLocked |= lock;
        return set(regionCode, value, v -> regionCode = v);
    }

    public boolean editMulticultural(boolean value, boolean lock) {
        multiculturalLocked |= lock;
        return set(multicultural, value, v -> multicultural = v);
    }

    public boolean editCategory(EventCategory value, boolean lock) {
        categoryLocked |= lock;
        return value != null && set(category, value, v -> category = v);
    }

    /** 수집한 카테고리를 반영한다. 관리자가 고쳤으면(잠금) 그대로 둔다. 바뀌었으면 true */
    public boolean collectCategory(EventCategory value) {
        return !categoryLocked && value != null && set(category, value, v -> category = v);
    }

    /** 잠기지 않았을 때만 키워드로 다시 계산한 다문화 여부를 반영한다 */
    public boolean recomputeMulticultural(boolean value) {
        return !multiculturalLocked && set(multicultural, value, v -> multicultural = v);
    }

    /** 관리자 삭제 (EVE-09). 행은 지우지 않는다 */
    public void delete(Long adminId) {
        status = STATUS_DELETED;
        updatedBy = adminId;
    }

    public void editedBy(Long adminId) {
        updatedBy = adminId;
    }

    private static <T> boolean set(T current, T value, Consumer<T> setter) {
        if (Objects.equals(current, value)) {
            return false;
        }
        setter.accept(value);
        return true;
    }

    /**
     * 일정(시작일·종료일·시작시각·종료시각)을 한 문자열로 만든다. 알림 신청자의 마지막 안내 일정과 비교한다 (NTF-02).
     * 형식 "시작일|종료일|시작시각|종료시각", 없는 값은 빈 칸.
     */
    public String scheduleSnapshot() {
        return scheduleSnapshot(startsOn, endsOn, startsAt, endsAt);
    }

    /** {@link #scheduleSnapshot()} 와 같은 형식 (바뀌기 전 일정을 만들 때) */
    public static String scheduleSnapshot(LocalDate startsOn, LocalDate endsOn, LocalTime startsAt, LocalTime endsAt) {
        return String.join("|", text(startsOn), text(endsOn), text(startsAt), text(endsAt));
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    /** 내용이 바뀌었다 (번역을 다시 만든다, COM-02) */
    public void increaseRevision() {
        revision++;
    }
}
