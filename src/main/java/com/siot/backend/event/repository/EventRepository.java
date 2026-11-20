package com.siot.backend.event.repository;

import com.siot.backend.event.domain.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    Optional<Event> findByExternalSourceAndExternalId(String externalSource, String externalId);

    /** 출처 간 중복 확인 후보 (같은 시작일, 상태 무관: 지운 행사도 다시 만들지 않게) */
    List<Event> findByStartsOn(LocalDate startsOn);

    /**
     * 번역을 만들거나 다시 만들 행사 (EVE-02): 보이는 행사(ACTIVE, 끝나지 않음) 중
     * en/zh/vi 번역이 없거나 원문 revision 과 다른 것. 시작일이 가까운 순.
     */
    @Query(value = """
            SELECT e.event_id FROM event e
            WHERE e.status = 'ACTIVE'
              AND (e.ends_on IS NULL OR e.ends_on >= :today)
              AND EXISTS (SELECT 1 FROM event_translation ko WHERE ko.event_id = e.event_id AND ko.language = 'ko')
              AND (SELECT count(*) FROM event_translation t
                   WHERE t.event_id = e.event_id AND t.language IN ('en', 'zh', 'vi')
                     AND t.source_revision = e.revision) < 3
            ORDER BY e.starts_on NULLS LAST, e.event_id
            """, nativeQuery = true)
    List<Long> findIdsNeedingTranslation(@Param("today") LocalDate today);
}
