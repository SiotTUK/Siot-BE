package com.siot.backend.translation.bill;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * RUL-07 최근 고지서 번역. 사진은 저장하지 않고 개인정보를 가린 결과(JSON)만 남긴다.
 * 목록·알림에 쓰는 값(서식, 기관, 세목, 금액, 기한)은 컬럼으로 따로 둔다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "bill_translation")
public class BillTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bill_translation_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 10)
    private String language;

    @Column(name = "document_type", length = 30)
    private String documentType;

    @Column(columnDefinition = "text")
    private String issuer;

    @Column(name = "tax_type", columnDefinition = "text")
    private String taxType;

    private Long amount;

    @Column(name = "due_date")
    private LocalDate dueDate;

    /** {@link BillTranslationResponse} JSON (id·reminder·savedAt 제외) */
    @Column(nullable = false, columnDefinition = "text")
    private String result;

    /** 납부 마감 D-7·D-1 알림 */
    @Column(nullable = false)
    private boolean reminder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    static BillTranslation of(Long userId, BillTranslationResponse response, String resultJson) {
        BillTranslation translation = new BillTranslation();
        translation.userId = userId;
        translation.language = response.language();
        translation.documentType = response.documentType();
        translation.issuer = response.issuer();
        translation.taxType = response.taxType();
        translation.amount = response.amount();
        translation.dueDate = response.dueDate();
        translation.result = resultJson;
        return translation;
    }

    /** 기한이 있고 지나지 않았으면 마감 알림을 켤 수 있다 */
    boolean canRemind(LocalDate today) {
        return dueDate != null && !dueDate.isBefore(today);
    }

    void changeReminder(boolean enabled) {
        this.reminder = enabled;
    }
}
