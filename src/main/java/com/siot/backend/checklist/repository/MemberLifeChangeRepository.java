package com.siot.backend.checklist.repository;

import com.siot.backend.checklist.domain.MemberLifeChange;
import com.siot.backend.checklist.domain.MemberLifeChangeId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberLifeChangeRepository extends JpaRepository<MemberLifeChange, MemberLifeChangeId> {

    List<MemberLifeChange> findByUserId(Long userId);

    Optional<MemberLifeChange> findByUserIdAndLifeChangeCode(Long userId, String lifeChangeCode);

    void deleteByUserIdAndLifeChangeCode(Long userId, String lifeChangeCode);

    void deleteByUserId(Long userId);
}
