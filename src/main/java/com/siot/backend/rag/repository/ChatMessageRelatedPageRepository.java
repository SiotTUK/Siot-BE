package com.siot.backend.rag.repository;

import com.siot.backend.rag.domain.ChatMessageRelatedPage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ChatMessageRelatedPageRepository extends JpaRepository<ChatMessageRelatedPage, Long> {

    /** CB-10: 대화 기록의 답변들에 저장된 관련 페이지 (한 번에 조회) */
    List<ChatMessageRelatedPage> findByMessageIdInOrderByMessageIdAscSortOrderAsc(Collection<Long> messageIds);
}
