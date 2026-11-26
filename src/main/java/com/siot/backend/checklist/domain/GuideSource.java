package com.siot.backend.checklist.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** #14 항목 출처 (공식 페이지 이름·URL). 한 항목에 여러 개, sortOrder 순 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "guide_source")
@IdClass(GuideSource.Id.class)
public class GuideSource {

    @jakarta.persistence.Id
    @Column(name = "guide_id")
    private Long guideId;

    @jakarta.persistence.Id
    @Column(name = "sort_order")
    private int sortOrder;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 2000)
    private String url;

    public static GuideSource of(Long guideId, int sortOrder, String name, String url) {
        GuideSource source = new GuideSource();
        source.guideId = guideId;
        source.sortOrder = sortOrder;
        source.name = name;
        source.url = url;
        return source;
    }

    @lombok.EqualsAndHashCode
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class Id implements Serializable {
        private Long guideId;
        private int sortOrder;
    }
}
