package com.siot.backend.checklist.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "guide_translation")
@IdClass(GuideTranslationId.class)
public class GuideTranslation {

    @Id
    @Column(length = 10)
    private String language;

    @Id
    @Column(name = "guide_id")
    private Long guideId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String summary;

    @Column(nullable = false, columnDefinition = "text")
    private String content;
}
