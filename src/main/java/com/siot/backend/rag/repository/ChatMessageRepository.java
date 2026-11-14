package com.siot.backend.rag.repository;

import com.siot.backend.rag.domain.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByConversationIdOrderByIdAsc(Long conversationId);

    /** 대화 목록의 제목(첫 질문) */
    Optional<ChatMessage> findFirstByConversationIdAndRoleOrderByIdAsc(Long conversationId, String role);
}
