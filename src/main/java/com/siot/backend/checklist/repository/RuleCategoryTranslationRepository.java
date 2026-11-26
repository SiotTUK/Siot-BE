package com.siot.backend.checklist.repository;

import com.siot.backend.checklist.domain.RuleCategoryTranslation;
import com.siot.backend.checklist.domain.RuleCategoryTranslationId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface RuleCategoryTranslationRepository
        extends JpaRepository<RuleCategoryTranslation, RuleCategoryTranslationId> {

    List<RuleCategoryTranslation> findByCategory(String category);

    List<RuleCategoryTranslation> findByCategoryIn(Collection<String> categories);

    List<RuleCategoryTranslation> findByLanguageAndCategoryIn(String language, Collection<String> categories);

    void deleteByCategory(String category);
}
