package com.siot.backend.checklist.repository;

import com.siot.backend.checklist.domain.GuideTranslation;
import com.siot.backend.checklist.domain.GuideTranslationId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuideTranslationRepository extends JpaRepository<GuideTranslation, GuideTranslationId> {
}
