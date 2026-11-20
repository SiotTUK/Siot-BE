package com.siot.backend.event.repository;

import com.siot.backend.event.domain.EventSubscription;
import com.siot.backend.event.domain.EventSubscriptionId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EventSubscriptionRepository extends JpaRepository<EventSubscription, EventSubscriptionId> {

    Optional<EventSubscription> findByUserIdAndEventId(Long userId, Long eventId);

    List<EventSubscription> findByEventIdAndEnabledTrue(Long eventId);

    Page<EventSubscription> findByUserIdAndEnabledTrueOrderByUpdatedAtDesc(Long userId, Pageable pageable);

    boolean existsByUserIdAndEventIdAndEnabledTrue(Long userId, Long eventId);

    void deleteByUserId(Long userId);
}
