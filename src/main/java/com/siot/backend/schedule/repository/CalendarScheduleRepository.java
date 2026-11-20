package com.siot.backend.schedule.repository;

import com.siot.backend.schedule.domain.CalendarSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface CalendarScheduleRepository extends JpaRepository<CalendarSchedule, Long> {

    List<CalendarSchedule> findByActiveTrueOrderByStartsOnAsc();

    List<CalendarSchedule> findByStartsOnBetweenAndActiveTrueOrderByStartsOnAsc(LocalDate start, LocalDate end);

    @Query("SELECT cs FROM CalendarSchedule cs WHERE cs.active = true AND cs.startsOn >= :startDate AND cs.startsOn <= :endDate")
    List<CalendarSchedule> findImpendingSchedules(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /** 납부 마감일이 그날인 일정 (NTF-01 마감 D-7·D-1) */
    List<CalendarSchedule> findByEndsOnAndActiveTrue(LocalDate endsOn);
}
