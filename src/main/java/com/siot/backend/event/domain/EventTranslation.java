package com.siot.backend.event.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "event_translation")
@IdClass(EventTranslationId.class)
public class EventTranslation {

    @Id
    @Column(length = 10)
    private String language;

    @Id
    @Column(name = "event_id")
    private Long eventId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    private String venue;

    @Column(name = "title_locked", nullable = false)
    private boolean titleLocked = false;

    @Column(name = "description_locked", nullable = false)
    private boolean descriptionLocked = false;

    @Column(name = "venue_locked", nullable = false)
    private boolean venueLocked = false;

    /** 이 행(ko 원문 또는 번역)을 만들 때의 event.revision. 다르면 다시 번역한다 */
    @Column(name = "source_revision")
    private Integer sourceRevision;

    public EventTranslation(Long eventId, String language, String title, String description, String venue,
                            Integer sourceRevision) {
        this.eventId = eventId;
        this.language = language;
        this.title = title;
        this.description = description;
        this.venue = venue;
        this.sourceRevision = sourceRevision;
    }

    /**
     * 수집한 원문(ko)을 반영한다. 잠긴 칸은 두고, null 인 수집 값은 기존 값을 지우지 않는다
     * (다른 출처가 채운 장소·설명이 사라지지 않게).
     *
     * @return 바뀐 칸이 있으면 true
     */
    public boolean collectOriginal(String newTitle, String newDescription, String newVenue) {
        boolean changed = false;
        if (!titleLocked && newTitle != null && !newTitle.equals(title)) {
            title = newTitle;
            changed = true;
        }
        if (!descriptionLocked && newDescription != null && !newDescription.equals(description)) {
            description = newDescription;
            changed = true;
        }
        if (!venueLocked && newVenue != null && !newVenue.equals(venue)) {
            venue = newVenue;
            changed = true;
        }
        return changed;
    }

    /**
     * 다른 출처의 같은 행사로 빈 칸(설명·장소)만 채운다.
     *
     * @return 채운 칸이 있으면 true
     */
    public boolean fillBlanks(String newDescription, String newVenue) {
        boolean changed = false;
        if (!descriptionLocked && isBlank(description) && !isBlank(newDescription)) {
            description = newDescription;
            changed = true;
        }
        if (!venueLocked && isBlank(venue) && !isBlank(newVenue)) {
            venue = newVenue;
            changed = true;
        }
        return changed;
    }

    /** 번역문을 반영한다 (잠긴 칸은 관리자가 고친 번역이므로 두고, 원문이 없는 칸은 비운다) */
    public void applyTranslation(String newTitle, String newDescription, String newVenue, int revision) {
        if (!titleLocked && newTitle != null) {
            title = newTitle;
        }
        if (!descriptionLocked) {
            description = newDescription;
        }
        if (!venueLocked) {
            venue = newVenue;
        }
        sourceRevision = revision;
    }

    /** 관리자가 원문(ko) 제목을 고친다 (EVE-08). 잠가서 수집이 덮어쓰지 않게 한다 */
    public boolean editTitle(String value, boolean lock) {
        titleLocked |= lock;
        if (Objects.equals(title, value)) {
            return false;
        }
        title = value;
        return true;
    }

    public boolean editDescription(String value, boolean lock) {
        descriptionLocked |= lock;
        if (Objects.equals(description, value)) {
            return false;
        }
        description = value;
        return true;
    }

    public boolean editVenue(String value, boolean lock) {
        venueLocked |= lock;
        if (Objects.equals(venue, value)) {
            return false;
        }
        venue = value;
        return true;
    }

    public void markSourceRevision(int revision) {
        sourceRevision = revision;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
