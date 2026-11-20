package com.siot.backend.event.collect;

import com.siot.backend.event.domain.EventTag;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 행사명·설명 키워드 규칙 (is_multicultural, event_tag). 수집 행사는 설명이 거의 없어 주로 행사명으로 정해진다.
 */
public final class EventKeywords {

    static final List<String> MULTICULTURAL = List.of(
            "다문화", "외국인", "이주민", "이주배경", "결혼이민", "글로벌", "국제교류", "세계인");
    static final List<String> FAMILY_KIDS = List.of(
            "어린이", "아동", "가족", "키즈", "유아", "청소년", "자녀", "아이와");
    static final List<String> KOREAN_CLASS = List.of(
            "한국어", "한글교실", "한글 교실");
    /** "신규"는 넣지 않는다 ("신규 회원 모집" 같은 행사가 걸린다) */
    static final List<String> NEWCOMER = List.of(
            "정착", "첫걸음", "입문", "생활안내", "생활 안내");

    private EventKeywords() {
    }

    public static boolean multicultural(String title, String description) {
        return containsAny(join(title, description), MULTICULTURAL);
    }

    /** 행사명으로 붙일 추천 태그 */
    public static Set<String> tags(String title) {
        Set<String> tags = new LinkedHashSet<>();
        if (containsAny(title, FAMILY_KIDS)) {
            tags.add(EventTag.FAMILY_KIDS);
        }
        if (containsAny(title, KOREAN_CLASS)) {
            tags.add(EventTag.KOREAN_CLASS);
        }
        if (containsAny(title, NEWCOMER)) {
            tags.add(EventTag.NEWCOMER);
        }
        return tags;
    }

    private static String join(String a, String b) {
        return (a == null ? "" : a) + " " + (b == null ? "" : b);
    }

    private static boolean containsAny(String text, List<String> keywords) {
        if (text == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
