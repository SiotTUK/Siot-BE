package com.siot.backend.event.repository;

import com.siot.backend.event.domain.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {

    @Query("SELECT e FROM Event e WHERE e.status = 'ACTIVE' AND e.startsOn = :targetDate")
    List<Event> findUpcomingEventsOnDate(@Param("targetDate") LocalDate targetDate);

    List<Event> findByStartsOnBetweenAndStatus(LocalDate start, LocalDate end, String status);
}
