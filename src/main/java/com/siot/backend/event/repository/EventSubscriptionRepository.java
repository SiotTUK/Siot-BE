package com.siot.backend.event.repository;

import com.siot.backend.event.domain.EventSubscription;
import com.siot.backend.event.domain.EventSubscriptionId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventSubscriptionRepository extends JpaRepository<EventSubscription, EventSubscriptionId> {

    /** 알림을 켜 둔 구독이 있는지 (EVE-04 상세의 "내 알림 신청 여부") */
    boolean existsByUserIdAndEventIdAndEnabledTrue(Long userId, Long eventId);
}
