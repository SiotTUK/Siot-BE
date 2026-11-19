package com.siot.backend.rag.repository;

import com.siot.backend.rag.domain.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    /** 아직 남아 있는 조각 ID (검색한 뒤 문서 재반영으로 지워진 조각을 거르기 위해, 임베딩은 읽지 않는다) */
    @Query("SELECT c.id FROM DocumentChunk c WHERE c.id IN :ids")
    List<Long> findExistingIds(@Param("ids") Collection<Long> ids);
}
