package com.siot.backend.translation.bill;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BillTranslationRepository extends JpaRepository<BillTranslation, Long> {

    /** 최근 번역 목록 (새것부터) */
    List<BillTranslation> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    Optional<BillTranslation> findByIdAndUserId(Long id, Long userId);

    /** 마감 알림 대상: 알림을 켠 번역 중 기한이 그날인 것 */
    List<BillTranslation> findByReminderTrueAndDueDate(LocalDate dueDate);
}
