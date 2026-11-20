package com.siot.backend.event.repository;

import com.siot.backend.event.domain.EventCollectionRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EventCollectionRunRepository extends JpaRepository<EventCollectionRun, Long> {

    List<EventCollectionRun> findBySourceOrderByIdDesc(String source);

    /** 마지막 실행 (상태 무관) */
    Optional<EventCollectionRun> findFirstBySourceOrderByIdDesc(String source);

    /** 마지막 성공 (EVE-10 최종 갱신 시각) */
    Optional<EventCollectionRun> findFirstBySourceAndStatusOrderByFinishedAtDesc(String source, String status);
}
