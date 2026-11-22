package com.siot.backend.rag.repository;

import com.siot.backend.rag.domain.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    /**
     * ADM-04: 현재 버전에서 내려온 버전 조각의 임베딩을 비운다. 글·쪽·조각 번호는 남긴다 (예전 답변 출처, CB-17).
     * 버전 전환 트랜잭션 안에서 부른다. 영속성 컨텍스트는 비우지 않는다 (같은 트랜잭션의 버전 엔티티를 계속 쓴다)
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE DocumentChunk c SET c.embedding = null WHERE c.documentVersion.id = :versionId AND c.embedding IS NOT NULL")
    int clearEmbeddings(@Param("versionId") Long versionId);

    /** 아직 남아 있는 조각 ID (검색한 뒤 문서 재반영으로 지워진 조각을 거르기 위해, 임베딩은 읽지 않는다) */
    @Query("SELECT c.id FROM DocumentChunk c WHERE c.id IN :ids")
    List<Long> findExistingIds(@Param("ids") Collection<Long> ids);
}
