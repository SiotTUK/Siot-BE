package com.siot.backend.checklist.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 조건별 문구. 예: CL-04 건강보험은 체류 목적(STUDY/WORK/FAMILY/OTHER)별 문구 + 공통 경고(WARNING).
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "guide_variant_translation")
@IdClass(GuideVariantTranslationId.class)
public class GuideVariantTranslation {

    @Id
    @Column(name = "guide_id")
    private Long guideId;

    @Id
    @Column(length = 10)
    private String language;

    @Id
    @Column(name = "variant_key", length = 20)
    private String variantKey;

    @Column(nullable = false, columnDefinition = "text")
    private String body;
}
