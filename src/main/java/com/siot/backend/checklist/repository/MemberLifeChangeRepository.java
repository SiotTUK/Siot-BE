package com.siot.backend.checklist.repository;

import com.siot.backend.checklist.domain.MemberLifeChange;
import com.siot.backend.checklist.domain.MemberLifeChangeId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MemberLifeChangeRepository extends JpaRepository<MemberLifeChange, MemberLifeChangeId> {

    List<MemberLifeChange> findByUserId(Long userId);

    Optional<MemberLifeChange> findByUserIdAndLifeChangeCode(Long userId, String lifeChangeCode);

    /** 기준일이 기간 안에 있는 생활 변화 (NTF-08 체류지 변경 신고 마감 알림) */
    List<MemberLifeChange> findByLifeChangeCodeAndBaseDateBetween(String lifeChangeCode, LocalDate from, LocalDate to);

    /** NTF-07 생활 변화 할 일 안내 변경 알림 대상 */
    List<MemberLifeChange> findByLifeChangeCode(String lifeChangeCode);

    void deleteByUserIdAndLifeChangeCode(Long userId, String lifeChangeCode);

    void deleteByUserId(Long userId);
}
