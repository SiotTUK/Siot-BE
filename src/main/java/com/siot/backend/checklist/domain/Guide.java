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

    /** CHECKLIST(체크리스트 규칙표 CL-xx) / LIFE_CHANGE_TASK(생활 변화 규칙표 LC-xx-n) 등 */
    @Column(nullable = false, length = 30)
    private String kind;

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
}
