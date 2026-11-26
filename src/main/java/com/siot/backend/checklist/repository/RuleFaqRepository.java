package com.siot.backend.checklist.repository;

import com.siot.backend.checklist.domain.RuleFaq;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RuleFaqRepository extends JpaRepository<RuleFaq, Long> {

    List<RuleFaq> findAllByOrderBySortOrderAscIdAsc();
}
