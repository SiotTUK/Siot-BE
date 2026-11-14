package com.siot.backend.rag.repository;

import com.siot.backend.rag.domain.ChatMessageSource;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ChatMessageSourceRepository extends JpaRepository<ChatMessageSource, Long> {

    @EntityGraph(attributePaths = {"chunk", "chunk.documentVersion", "chunk.documentVersion.document"})
    List<ChatMessageSource> findByMessageIdInOrderBySourceOrderAsc(Collection<Long> messageIds);
}
