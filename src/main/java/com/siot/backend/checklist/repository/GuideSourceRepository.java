package com.siot.backend.checklist.repository;

import com.siot.backend.checklist.domain.GuideSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.Collection;
import java.util.List;

public interface GuideSourceRepository extends JpaRepository<GuideSource, GuideSource.Id> {

    List<GuideSource> findByGuideIdOrderBySortOrderAsc(Long guideId);

    List<GuideSource> findByGuideIdIn(Collection<Long> guideIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    void deleteByGuideId(Long guideId);
}
