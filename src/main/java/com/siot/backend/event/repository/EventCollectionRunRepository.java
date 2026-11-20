package com.siot.backend.event.repository;

import com.siot.backend.event.domain.EventCollectionRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventCollectionRunRepository extends JpaRepository<EventCollectionRun, Long> {

    List<EventCollectionRun> findBySourceOrderByIdDesc(String source);
}
