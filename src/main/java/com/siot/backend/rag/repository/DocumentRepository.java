package com.siot.backend.rag.repository;

import com.siot.backend.rag.domain.Document;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    /** 같은 문서에 버전을 동시에 올리거나 재실행하지 않도록 문서 행을 잠근다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Document d WHERE d.id = :id")
    Optional<Document> findByIdForUpdate(@Param("id") Long id);
}
