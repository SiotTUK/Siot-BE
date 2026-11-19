package com.siot.backend.rag.repository;

import com.siot.backend.rag.domain.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByConversationIdOrderByIdAsc(Long conversationId);

    /** 대화 목록의 제목(첫 질문). 페이지의 대화들을 한 번에 조회한다 (대화마다 조회하면 N+1) */
    @Query("""
            SELECT m FROM ChatMessage m
            WHERE m.id IN (
                SELECT MIN(f.id) FROM ChatMessage f
                WHERE f.conversation.id IN :conversationIds AND f.role = 'USER'
                GROUP BY f.conversation.id)""")
    List<ChatMessage> findFirstQuestions(@Param("conversationIds") Collection<Long> conversationIds);

    /** ADM-08: 답변 바로 앞의 질문 */
    Optional<ChatMessage> findFirstByConversationIdAndRoleAndIdLessThanOrderByIdDesc(
            Long conversationId, String role, Long id);

    /** ADM-08: 부정 평가 답변 / 근거 없음 답변 (최신순) */
    @Query(value = """
            SELECT m FROM ChatMessage m JOIN FETCH m.conversation
            WHERE m.role = 'ASSISTANT'
              AND ((:negative = true AND m.helpful = false)
                   OR (:noEvidence = true AND m.answerStatus = :noEvidenceStatus))
              AND (:reviewed IS NULL OR m.reviewed = :reviewed)
            ORDER BY m.createdAt DESC, m.id DESC""",
            countQuery = """
            SELECT count(m) FROM ChatMessage m
            WHERE m.role = 'ASSISTANT'
              AND ((:negative = true AND m.helpful = false)
                   OR (:noEvidence = true AND m.answerStatus = :noEvidenceStatus))
              AND (:reviewed IS NULL OR m.reviewed = :reviewed)""")
    Page<ChatMessage> findReviewTargets(@Param("negative") boolean negative,
                                        @Param("noEvidence") boolean noEvidence,
                                        @Param("noEvidenceStatus") String noEvidenceStatus,
                                        @Param("reviewed") Boolean reviewed,
                                        Pageable pageable);
}
