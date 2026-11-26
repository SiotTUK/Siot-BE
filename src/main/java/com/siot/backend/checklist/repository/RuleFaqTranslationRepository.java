package com.siot.backend.checklist.repository;

import com.siot.backend.checklist.domain.RuleFaqTranslation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface RuleFaqTranslationRepository extends JpaRepository<RuleFaqTranslation, RuleFaqTranslation.Id> {

    List<RuleFaqTranslation> findByFaqIdIn(Collection<Long> faqIds);
}
