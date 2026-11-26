package com.siot.backend.checklist.repository;

import com.siot.backend.checklist.domain.GuideTranslation;
import com.siot.backend.checklist.domain.GuideTranslationId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GuideTranslationRepository extends JpaRepository<GuideTranslation, GuideTranslationId> {

    Optional<GuideTranslation> findByGuideIdAndLanguage(Long guideId, String language);

    List<GuideTranslation> findByGuideIdInAndLanguage(Collection<Long> guideIds, String language);

    List<GuideTranslation> findByGuideId(Long guideId);

    /** 관리자 생활수칙 목록: 항목별 번역 언어 */
    List<GuideTranslation> findByGuideIdIn(Collection<Long> guideIds);
}
