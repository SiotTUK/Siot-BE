package com.siot.backend.checklist.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * #14 생활수칙 카테고리의 FE 키와 화면 순서. 이름(한국어)은 guide.category, 번역은 rule_category_translation.
 * 관리자가 새 이름으로 등록한 카테고리는 행이 없거나 키가 없다 (목록 뒤에 이름순).
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "rule_category")
public class RuleCategory {

    public static final int DEFAULT_SORT_ORDER = 1000;

    @Id
    @Column(length = 100)
    private String category;

    /** FE 키 (order, waste, traffic, tax, living, support) */
    @Column(name = "category_key", length = 30)
    private String categoryKey;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = DEFAULT_SORT_ORDER;

    public static RuleCategory of(String category, String categoryKey, int sortOrder) {
        RuleCategory ruleCategory = new RuleCategory();
        ruleCategory.category = category;
        ruleCategory.categoryKey = categoryKey;
        ruleCategory.sortOrder = sortOrder;
        return ruleCategory;
    }
}
