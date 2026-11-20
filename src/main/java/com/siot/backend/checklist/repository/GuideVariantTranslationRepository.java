package com.siot.backend.checklist.repository;

import com.siot.backend.checklist.domain.GuideVariantTranslation;
import com.siot.backend.checklist.domain.GuideVariantTranslationId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GuideVariantTranslationRepository extends JpaRepository<GuideVariantTranslation, GuideVariantTranslationId> {

    List<GuideVariantTranslation> findByGuideIdAndLanguage(Long guideId, String language);

    Optional<GuideVariantTranslation> findByGuideIdAndLanguageAndVariantKey(Long guideId, String language, String variantKey);
}
