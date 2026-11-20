package com.siot.backend.event.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 행사 추천 태그 (EVE-05). 수집할 때 행사명 키워드로 붙인다.
 * FAMILY_KIDS = 자녀 동반(거주 형태 KIDS), KOREAN_CLASS = 한국어 교육(관심사 KOREAN), NEWCOMER = 새로 온 사람(userType NEW)
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "event_tag")
@IdClass(EventTagId.class)
public class EventTag {

    public static final String FAMILY_KIDS = "FAMILY_KIDS";
    public static final String KOREAN_CLASS = "KOREAN_CLASS";
    public static final String NEWCOMER = "NEWCOMER";

    @Id
    @Column(name = "event_id")
    private Long eventId;

    @Id
    @Column(length = 30)
    private String tag;

    public EventTag(Long eventId, String tag) {
        this.eventId = eventId;
        this.tag = tag;
    }
}
