package com.siot.backend.rag.repository;

import com.siot.backend.rag.domain.ChatConversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatConversationRepository extends JpaRepository<ChatConversation, Long> {

    /** NFR-03: 본인 대화만 */
    Optional<ChatConversation> findByIdAndUserId(Long id, Long userId);

    Page<ChatConversation> findByUserIdOrderByUpdatedAtDesc(Long userId, Pageable pageable);
}
