package com.siot.backend.event.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
}
