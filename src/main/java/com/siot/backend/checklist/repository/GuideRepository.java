package com.siot.backend.checklist.repository;

import com.siot.backend.checklist.domain.Guide;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GuideRepository extends JpaRepository<Guide, Long> {

    Optional<Guide> findByCode(String code);

    List<Guide> findByCodeIn(Collection<String> codes);

    List<Guide> findByKindOrderBySortOrderAsc(String kind);

    List<Guide> findByKindAndCategoryOrderBySortOrderAsc(String kind, String category);

    /** RUL-04: 자주 묻는 질문 TOP 5 (RULE만, faq = true, 정렬순) */
    List<Guide> findTop5ByKindAndFaqTrueAndDeletedAtIsNullOrderBySortOrderAsc(String kind);

    List<Guide> findByKindAndLifeChangeCodeOrderBySortOrderAsc(String kind, String lifeChangeCode);

    @Query("SELECT DISTINCT g.category FROM Guide g WHERE g.kind = :kind AND g.category IS NOT NULL AND g.deletedAt IS NULL ORDER BY g.category ASC")
    List<String> findDistinctCategoriesByKind(@Param("kind") String kind);
}
