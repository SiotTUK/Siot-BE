package com.siot.backend.checklist.domain;

import com.siot.backend.global.common.SupportedLanguages;

import java.util.Map;

/**
 * 체크리스트·생활 변화 할 일의 분류(guide.category) 표시 이름 (4개 언어).
 * 생활수칙은 관리자가 정한 한국어 이름을 쓰고 rule_category_translation 으로 번역하므로 여기 없다.
 */
public final class ChecklistCategories {

    private static final Map<String, Map<String, String>> LABELS = Map.of(
            "IMMIGRATION", Map.of("ko", "출입국·체류", "en", "Immigration & stay", "zh", "出入境·居留", "vi", "Xuất nhập cảnh & lưu trú"),
            "HEALTH_INSURANCE", Map.of("ko", "건강보험", "en", "Health insurance", "zh", "健康保险", "vi", "Bảo hiểm y tế"),
            "FAMILY", Map.of("ko", "가족", "en", "Family", "zh", "家庭", "vi", "Gia đình"),
            "TAX", Map.of("ko", "세금", "en", "Tax", "zh", "税金", "vi", "Thuế"),
            "EDUCATION", Map.of("ko", "교육", "en", "Education", "zh", "教育", "vi", "Giáo dục"),
            "SETTLING", Map.of("ko", "생활 정착", "en", "Settling in", "zh", "生活适应", "vi", "Ổn định cuộc sống"),
            "CAR", Map.of("ko", "자동차", "en", "Car", "zh", "汽车", "vi", "Ô tô"),
            "EMPLOYMENT", Map.of("ko", "취업", "en", "Employment", "zh", "就业", "vi", "Việc làm"));

    private ChecklistCategories() {
    }

    /** 분류 표시 이름. 모르는 분류면 코드 그대로, null 이면 null */
    public static String label(String category, String language) {
        if (category == null) {
            return null;
        }
        Map<String, String> labels = LABELS.get(category);
        if (labels == null) {
            return category;
        }
        return labels.getOrDefault(SupportedLanguages.orDefault(language), labels.get(SupportedLanguages.DEFAULT));
    }
}
