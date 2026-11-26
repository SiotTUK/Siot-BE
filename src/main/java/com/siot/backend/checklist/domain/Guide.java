package com.siot.backend.checklist.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

/**
 * 체크리스트 항목·생활 변화 할 일의 콘텐츠. 문구는 {@link GuideTranslation} 에 언어별로 저장한다.
 * 표시 조건·기한 계산식·알림 시점은 규칙표대로 코드에서 정의하고 {@link #code} 로 연결한다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "guide")
public class Guide {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "guide_id")
    private Long id;

    /** 항목 ID (CL-01, LC-01-1 ...) */
    @Column(nullable = false, length = 50, unique = true)
    private String code;

    /** CHECKLIST(체크리스트 규칙표 CL-xx) / LIFE_CHANGE_TASK(생활 변화 규칙표 LC-xx-n) / RULE(생활수칙) */
    @Column(nullable = false, length = 30)
    private String kind;

    /** RUL-04 자주 묻는 질문 TOP5 에 표시 (생활수칙만). 순서는 {@link #sortOrder} */
    @Column(name = "is_faq", nullable = false)
    private boolean faq = false;

    /**
     * NTF-07 실제 안내 내용이 바뀐 시각. null = 등록 후 바뀐 적 없음.
     * {@link #updatedAt} 은 오타 수정에도 바뀌므로 변경 표시에는 이 값을 쓴다.
     */
    @Column(name = "notice_changed_at")
    private OffsetDateTime noticeChangedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 10)
    private ItemType itemType = ItemType.REQUIRED;

    /** 생활 변화 할 일이 속한 변화 ID (LC-01 ~ LC-08). 체크리스트 항목은 null */
    @Column(name = "life_change_code", length = 10)
    private String lifeChangeCode;

    @Column(length = 100)
    private String category;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    /** 출처 링크 (출처 이름은 언어별 source_name) */
    @Column(name = "source_url", columnDefinition = "text")
    private String sourceUrl;

    /** 문의 전화 (예: 1345) */
    private String contact;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** CHK-06 updatedAt */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * 관리자가 문구를 저장했다. updated_at(CHK-06)은 항상 바뀌고,
     * NTF-07 '안내 내용 변경'으로 저장한 경우에만 notice_changed_at 도 바뀐다 (오타 수정 등은 바꾸지 않음).
     */
    public void recordEdit(Long adminId, boolean noticeChanged, OffsetDateTime now) {
        this.updatedBy = adminId;
        this.updatedAt = now;
        if (noticeChanged) {
            this.noticeChangedAt = now;
        }
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void softDelete(Long adminId, OffsetDateTime now) {
        this.deletedAt = now;
        this.updatedBy = adminId;
        this.updatedAt = now;
    }

    /**
     * 출처 링크·문의 전화를 바꾼다 (NTF-07 관리자 검토 후 수정). null 이면 그대로, 빈 문자열이면 지운다.
     */
    public void updateSourceAndContact(String sourceUrl, String contact, Long adminId, OffsetDateTime now) {
        if (sourceUrl != null) this.sourceUrl = sourceUrl.isBlank() ? null : sourceUrl.strip();
        if (contact != null) this.contact = contact.isBlank() ? null : contact.strip();
        this.updatedBy = adminId;
        this.updatedAt = now;
    }

    public void updateRule(String category, int sortOrder, boolean faq, String contact, String sourceUrl, Long adminId, OffsetDateTime now) {
        if (category != null) this.category = category;
        this.sortOrder = sortOrder;
        this.faq = faq;
        if (contact != null) this.contact = contact;
        if (sourceUrl != null) this.sourceUrl = sourceUrl;
        this.updatedBy = adminId;
        this.updatedAt = now;
    }

    public static Guide create(String code, String kind, ItemType itemType, String category, int sortOrder, String lifeChangeCode, boolean faq) {
        Guide guide = new Guide();
        guide.code = code;
        guide.kind = kind;
        guide.itemType = itemType != null ? itemType : ItemType.REQUIRED;
        guide.category = category;
        guide.sortOrder = sortOrder;
        guide.lifeChangeCode = lifeChangeCode;
        guide.faq = faq;
        return guide;
    }
}
