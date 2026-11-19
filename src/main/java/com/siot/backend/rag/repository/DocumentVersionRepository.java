package com.siot.backend.rag.repository;

import com.siot.backend.rag.domain.DocumentVersion;
import com.siot.backend.rag.domain.ProcessingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {

    List<DocumentVersion> findByDocumentIdOrderByVersionNoDesc(Long documentId);

    /** 목록 화면용: 여러 문서의 버전 (최신 먼저) */
    List<DocumentVersion> findByDocumentIdInOrderByVersionNoDesc(Collection<Long> documentIds);

    Optional<DocumentVersion> findByDocumentIdAndCurrentTrue(Long documentId);

    boolean existsByDocumentIdAndProcessingStatusIn(Long documentId, Collection<ProcessingStatus> statuses);

    /** 서버가 다시 시작될 때 이어서 처리할 버전 (오래된 것부터) */
    List<DocumentVersion> findByProcessingStatusInOrderByIdAsc(Collection<ProcessingStatus> statuses);

    @Query("SELECT COALESCE(MAX(v.versionNo), 0) FROM DocumentVersion v WHERE v.document.id = :documentId")
    int findMaxVersionNo(@Param("documentId") Long documentId);

    /** 버전별 조각 수 [versionId, count] */
    @Query("SELECT c.documentVersion.id, COUNT(c) FROM DocumentChunk c WHERE c.documentVersion.id IN :versionIds "
            + "GROUP BY c.documentVersion.id")
    List<Object[]> countChunks(@Param("versionIds") Collection<Long> versionIds);
}
