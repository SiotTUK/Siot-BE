package com.siot.backend.checklist.repository;

import com.siot.backend.checklist.domain.Guide;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GuideRepository extends JpaRepository<Guide, Long> {

    Optional<Guide> findByCode(String code);
}
