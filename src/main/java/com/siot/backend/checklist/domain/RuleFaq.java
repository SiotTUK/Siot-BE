package com.siot.backend.checklist.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** #14 RUL-04 생활수칙 자주 묻는 질문. 질문·답변은 {@link RuleFaqTranslation}, guideId = 자세히 볼 항목 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "rule_faq")
public class RuleFaq {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "faq_id")
    private Long id;

    @Column(name = "guide_id")
    private Long guideId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
