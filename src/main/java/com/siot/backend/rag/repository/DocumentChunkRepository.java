package com.siot.backend.rag.repository;

import com.siot.backend.rag.domain.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {
}
