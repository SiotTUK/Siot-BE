package com.siot.backend.rag.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "chat_message_source")
public class ChatMessageSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "source_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private ChatMessage message;

    /** 문서가 삭제되면 NULL (답변 근거 기록은 남긴다) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chunk_id")
    private DocumentChunk chunk;

    @Column(name = "relevance_score")
    private Double relevanceScore;

    @Column(name = "source_order", nullable = false)
    private int sourceOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static ChatMessageSource of(ChatMessage message, DocumentChunk chunk, double relevanceScore, int order) {
        ChatMessageSource source = new ChatMessageSource();
        source.message = message;
        source.chunk = chunk;
        source.relevanceScore = relevanceScore;
        source.sourceOrder = order;
        return source;
    }
}
