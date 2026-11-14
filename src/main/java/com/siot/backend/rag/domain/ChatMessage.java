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
@Table(name = "chat_message")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private ChatConversation conversation;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "answer_status", length = 30)
    private String answerStatus;

    @Column(name = "is_helpful")
    private Boolean helpful;

    @Column(name = "feedback_comment", columnDefinition = "text")
    private String feedbackComment;

    /** CB-09: 답변만. 질문에 응급 키워드가 있어 119/112 를 함께 표시 */
    @Column(name = "is_emergency", nullable = false)
    private boolean emergency = false;

    // ADM-08: 부정 평가 답변 / 미응답 질문 관리자 처리
    @Column(name = "is_reviewed", nullable = false)
    private boolean reviewed = false;

    @Column(name = "admin_memo", columnDefinition = "text")
    private String adminMemo;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static final String ROLE_USER = "USER";
    public static final String ROLE_ASSISTANT = "ASSISTANT";

    /** @param content 개인정보를 가린 질문 (CB-05) */
    public static ChatMessage question(ChatConversation conversation, String content) {
        ChatMessage message = new ChatMessage();
        message.conversation = conversation;
        message.role = ROLE_USER;
        message.content = content;
        return message;
    }

    public static ChatMessage answer(ChatConversation conversation, String content, AnswerStatus status,
                                     boolean emergency) {
        ChatMessage message = new ChatMessage();
        message.conversation = conversation;
        message.role = ROLE_ASSISTANT;
        message.content = content;
        message.answerStatus = status.name();
        message.emergency = emergency;
        return message;
    }

    public boolean isAnswer() {
        return ROLE_ASSISTANT.equals(role);
    }

    /** ADM-08: 관리자 처리 여부·메모 기록 */
    public void review(boolean reviewed, String adminMemo, Long adminId, OffsetDateTime now) {
        this.reviewed = reviewed;
        this.adminMemo = adminMemo;
        this.reviewedBy = adminId;
        this.reviewedAt = now;
    }

    /** CB-04: 답변 평가 (다시 평가하면 덮어쓴다) */
    public void recordFeedback(boolean helpful, String comment) {
        this.helpful = helpful;
        this.feedbackComment = comment;
    }
}
