package com.siot.backend.checklist.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

/** RUL-13 생활수칙 카테고리명 번역 (en / zh / vi). 원문은 guide.category 의 한국어 이름이다 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "rule_category_translation")
@IdClass(RuleCategoryTranslationId.class)
public class RuleCategoryTranslation {

    /** 한국어 카테고리명 (guide.category) */
    @Id
    @Column(length = 100)
    private String category;

    @Id
    @Column(length = 10)
    private String language;

    @Column(nullable = false, length = 100)
    private String name;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static RuleCategoryTranslation of(String category, String language, String name) {
        RuleCategoryTranslation translation = new RuleCategoryTranslation();
        translation.category = category;
        translation.language = language;
        translation.name = name;
        return translation;
    }

    public void rename(String name) {
        this.name = name;
    }
}
