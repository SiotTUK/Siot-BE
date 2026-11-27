package com.siot.backend.event.repository;

import com.siot.backend.event.domain.EventSubscription;
import com.siot.backend.event.domain.EventSubscriptionId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EventSubscriptionRepository extends JpaRepository<EventSubscription, EventSubscriptionId> {

    Optional<EventSubscription> findByUserIdAndEventId(Long userId, Long eventId);

    List<EventSubscription> findByEventIdAndEnabledTrue(Long eventId);
    /** 내 알림 신청 목록: 지운 행사(DELETED)와 공개하지 않는 지역의 행사는 회원에게 보이지 않으므로 뺀다. 최근 신청 순 */
    @Query(value = """
            SELECT s FROM EventSubscription s, Event e
            WHERE e.id = s.eventId AND s.userId = :userId AND s.enabled = true AND e.status = 'ACTIVE'
              AND e.regionCode IN :publicRegions
            ORDER BY s.updatedAt DESC
            """,
            countQuery = """
            SELECT count(s) FROM EventSubscription s, Event e
            WHERE e.id = s.eventId AND s.userId = :userId AND s.enabled = true AND e.status = 'ACTIVE'
              AND e.regionCode IN :publicRegions
            """)
    Page<EventSubscription> findMyActive(@Param("userId") Long userId,
                                         @Param("publicRegions") Collection<String> publicRegions, Pageable pageable);

    /** 알림을 켜 둔 구독이 있는지 (EVE-04 상세의 "내 알림 신청 여부") */
    boolean existsByUserIdAndEventIdAndEnabledTrue(Long userId, Long eventId);

    void deleteByUserId(Long userId);

    /** NTF-02 일정 변경 확인 대상: 알림을 켠 신청 중 보이는 행사(ACTIVE)가 끝나지 않은 것 */
    @Query("""
            SELECT s FROM EventSubscription s, Event e
            WHERE e.id = s.eventId AND s.enabled = true AND e.status = 'ACTIVE'
              AND COALESCE(e.endsOn, e.startsOn) >= :today
            """)
    List<EventSubscription> findEnabledForCurrentEvents(@Param("today") LocalDate today);
}
