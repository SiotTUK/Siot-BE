package com.siot.backend.checklist.repository;

import com.siot.backend.checklist.domain.RuleCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RuleCategoryRepository extends JpaRepository<RuleCategory, String> {

    Optional<RuleCategory> findByCategoryKey(String categoryKey);
}
