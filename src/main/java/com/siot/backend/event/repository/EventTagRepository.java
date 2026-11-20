package com.siot.backend.event.repository;

import com.siot.backend.event.domain.EventTag;
import com.siot.backend.event.domain.EventTagId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface EventTagRepository extends JpaRepository<EventTag, EventTagId> {

    List<EventTag> findByEventId(Long eventId);

    List<EventTag> findByEventIdIn(Collection<Long> eventIds);
}
