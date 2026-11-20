package com.siot.backend.checklist.repository;

import com.siot.backend.checklist.domain.ChecklistProgress;
import com.siot.backend.checklist.domain.ChecklistProgressId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChecklistProgressRepository extends JpaRepository<ChecklistProgress, ChecklistProgressId> {

    List<ChecklistProgress> findByUserId(Long userId);

    Optional<ChecklistProgress> findByUserIdAndGuideIdAndPeriodKey(Long userId, Long guideId, String periodKey);

    void deleteByUserId(Long userId);
}
