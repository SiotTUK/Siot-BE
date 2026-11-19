package com.siot.backend.rag.domain;

import com.siot.backend.rag.dto.RelatedPage;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * CB-10: 답변과 함께 보여 준 '관련 내용 보기' 링크. 대화 기록에서 처음과 같은 링크를 다시 보여 주기 위해 저장한다.
 * 제목은 답한 언어의 저장 시점 값이다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "chat_message_related_page")
public class ChatMessageRelatedPage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "related_page_id")
    private Long id;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** CHECKLIST / LIFE_CHANGE_TASK / RULE / EVENT */
    @Column(name = "page_type", nullable = false, length = 30)
    private String pageType;

    @Column(nullable = false, length = 100)
    private String code;

    @Column(nullable = false, columnDefinition = "text")
    private String title;

    public static ChatMessageRelatedPage of(Long messageId, int sortOrder, RelatedPage page) {
        ChatMessageRelatedPage entity = new ChatMessageRelatedPage();
        entity.messageId = messageId;
        entity.sortOrder = sortOrder;
        entity.pageType = page.type();
        entity.code = page.code();
        entity.title = page.title();
        return entity;
    }

    public RelatedPage toRelatedPage() {
        return new RelatedPage(pageType, code, title);
    }
}
