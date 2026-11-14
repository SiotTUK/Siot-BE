package com.siot.backend.rag.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "chat_conversation")
public class ChatConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "conversation_id")
    private Long id;

    /** 대화는 회원만 (비회원 사용 없음) */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 10)
    private String language = "ko";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static ChatConversation start(Long userId, String language) {
        ChatConversation conversation = new ChatConversation();
        conversation.userId = userId;
        conversation.language = language;
        return conversation;
    }

    /** 새 메시지가 오가면 대화 목록 정렬(최근 대화 순)을 위해 수정 시각을 갱신한다 */
    public void touch(OffsetDateTime now) {
        this.updatedAt = now;
    }
}
