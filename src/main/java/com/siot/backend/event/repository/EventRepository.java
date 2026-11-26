package com.siot.backend.event.repository;

import com.siot.backend.event.domain.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    // ---------------------------------------------------------------- 행사 알림 (NotificationScheduler)

    /** NTF-02 행사 시작 알림 후보 */
    List<Event> findByStartsOnBetweenAndStatus(LocalDate start, LocalDate end, String status);

    /** NTF-02 신청 마감 D-1 알림 후보: 신청 마감 시각이 [from, to] 안인 행사 */
    List<Event> findByStatusAndApplicationDeadlineBetween(String status, OffsetDateTime from, OffsetDateTime to);

    // ---------------------------------------------------------------- 수집·번역

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

    // ---------------------------------------------------------------- 회원 조회 (EVE-03~05, EVE-10)
    // 종료일이 없으면 시작일, 시작일이 없으면 종료일로 본다 (둘 중 하나는 늘 있다)

    /** 진행 예정·진행 중 (종료일 >= today), 기간 [from, to] 와 겹치는 것. 시작일 가까운 순 */
    @Query(value = """
            SELECT e FROM Event e
            WHERE e.status = 'ACTIVE'
              AND COALESCE(e.endsOn, e.startsOn) >= :today
              AND (:anyRegion = true OR e.regionCode = :region)
              AND COALESCE(e.endsOn, e.startsOn) >= :from
              AND COALESCE(e.startsOn, e.endsOn) <= :to
            ORDER BY COALESCE(e.startsOn, e.endsOn) ASC, e.id ASC
            """,
            countQuery = """
            SELECT count(e) FROM Event e
            WHERE e.status = 'ACTIVE'
              AND COALESCE(e.endsOn, e.startsOn) >= :today
              AND (:anyRegion = true OR e.regionCode = :region)
              AND COALESCE(e.endsOn, e.startsOn) >= :from
              AND COALESCE(e.startsOn, e.endsOn) <= :to
            """)
    Page<Event> findUpcoming(@Param("today") LocalDate today, @Param("anyRegion") boolean anyRegion,
                             @Param("region") String region, @Param("from") LocalDate from,
                             @Param("to") LocalDate to, Pageable pageable);

    /** 지난 행사 (종료일 < today), 기간 [from, to] 와 겹치는 것. 최근에 끝난 순 */
    @Query(value = """
            SELECT e FROM Event e
            WHERE e.status = 'ACTIVE'
              AND COALESCE(e.endsOn, e.startsOn) < :today
              AND (:anyRegion = true OR e.regionCode = :region)
              AND COALESCE(e.endsOn, e.startsOn) >= :from
              AND COALESCE(e.startsOn, e.endsOn) <= :to
            ORDER BY COALESCE(e.endsOn, e.startsOn) DESC, COALESCE(e.startsOn, e.endsOn) DESC, e.id DESC
            """,
            countQuery = """
            SELECT count(e) FROM Event e
            WHERE e.status = 'ACTIVE'
              AND COALESCE(e.endsOn, e.startsOn) < :today
              AND (:anyRegion = true OR e.regionCode = :region)
              AND COALESCE(e.endsOn, e.startsOn) >= :from
              AND COALESCE(e.startsOn, e.endsOn) <= :to
            """)
    Page<Event> findPast(@Param("today") LocalDate today, @Param("anyRegion") boolean anyRegion,
                         @Param("region") String region, @Param("from") LocalDate from,
                         @Param("to") LocalDate to, Pageable pageable);

    /** 기간 [from, to] 에 하루라도 걸치는 보이는 행사 (EVE-03 달력). 시작일 순 */
    @Query("""
            SELECT e FROM Event e
            WHERE e.status = 'ACTIVE'
              AND COALESCE(e.endsOn, e.startsOn) >= :from
              AND COALESCE(e.startsOn, e.endsOn) <= :to
            ORDER BY COALESCE(e.startsOn, e.endsOn) ASC, e.id ASC
            """)
    List<Event> findOverlapping(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 진행 예정·진행 중인 보이는 행사 전부 (EVE-05 추천 후보) */
    @Query("""
            SELECT e FROM Event e
            WHERE e.status = 'ACTIVE' AND COALESCE(e.endsOn, e.startsOn) >= :today
            """)
    List<Event> findCurrent(@Param("today") LocalDate today);

    /** 관리자 등록 중복 확인 후보 (EVE-07): 시작일이 같은 ACTIVE 행사 */
    @Query("""
            SELECT e FROM Event e
            WHERE e.status = 'ACTIVE' AND COALESCE(e.startsOn, e.endsOn) = :startsOn
            """)
    List<Event> findActiveStartingOn(@Param("startsOn") LocalDate startsOn);

    // ---------------------------------------------------------------- 관리자 목록 (ADM-05)

    /** 상태·지역·출처·제목(ko, 소문자 LIKE 패턴, 이스케이프 문자 !)으로 거른다. 최근 등록 순 */
    @Query(value = """
            SELECT e FROM Event e
            WHERE (:anyStatus = true OR e.status = :status)
              AND (:anyRegion = true OR e.regionCode = :region)
              AND (:anySource = true OR e.externalSource = :source)
              AND (:anyTitle = true OR EXISTS (
                    SELECT 1 FROM EventTranslation t
                    WHERE t.eventId = e.id AND t.language = 'ko' AND LOWER(t.title) LIKE :titlePattern ESCAPE '!'))
            ORDER BY e.id DESC
            """,
            countQuery = """
            SELECT count(e) FROM Event e
            WHERE (:anyStatus = true OR e.status = :status)
              AND (:anyRegion = true OR e.regionCode = :region)
              AND (:anySource = true OR e.externalSource = :source)
              AND (:anyTitle = true OR EXISTS (
                    SELECT 1 FROM EventTranslation t
                    WHERE t.eventId = e.id AND t.language = 'ko' AND LOWER(t.title) LIKE :titlePattern ESCAPE '!'))
            """)
    Page<Event> search(@Param("anyStatus") boolean anyStatus, @Param("status") String status,
                       @Param("anyRegion") boolean anyRegion, @Param("region") String region,
                       @Param("anySource") boolean anySource, @Param("source") String source,
                       @Param("anyTitle") boolean anyTitle, @Param("titlePattern") String titlePattern,
                       Pageable pageable);
}
