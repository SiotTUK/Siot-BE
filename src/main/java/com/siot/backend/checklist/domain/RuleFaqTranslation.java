package com.siot.backend.checklist.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** #14 FAQ 질문·답변 (ko / en / zh / vi) */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "rule_faq_translation")
@IdClass(RuleFaqTranslation.Id.class)
public class RuleFaqTranslation {

    @jakarta.persistence.Id
    @Column(name = "faq_id")
    private Long faqId;

    @jakarta.persistence.Id
    @Column(length = 10)
    private String language;

    @Column(nullable = false, columnDefinition = "text")
    private String question;

    @Column(nullable = false, columnDefinition = "text")
    private String answer;

    public static RuleFaqTranslation create(Long faqId, String language, String question, String answer) {
        RuleFaqTranslation translation = new RuleFaqTranslation();
        translation.faqId = faqId;
        translation.language = language;
        translation.question = question;
        translation.answer = answer;
        return translation;
    }

    public void update(String question, String answer) {
        this.question = question;
        this.answer = answer;
    }

    @lombok.EqualsAndHashCode
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class Id implements Serializable {
        private Long faqId;
        private String language;
    }
}
