package com.siot.backend.checklist.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class GuideVariantTranslationId implements Serializable {
    private Long guideId;
    private String language;
    private String variantKey;
}
