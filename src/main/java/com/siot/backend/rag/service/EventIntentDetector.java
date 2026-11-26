package com.siot.backend.rag.service;

import com.siot.backend.event.domain.EventCategory;

import java.text.Normalizer;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * CB-10: 행사·축제를 묻는 질문인지 감지한다 (4개 언어 단어).
 * 감지되면 관련 페이지에 다가오는 행사 상세 링크를 함께 보여 준다.
 * 행사는 RAG 문서에 없어서 답변 근거와 연결할 수 없으므로, 질문 단어로만 판단한다.
 */
public final class EventIntentDetector {

    private static final List<String> WORDS = List.of(
            // ko
            "행사", "축제", "공연", "전시", "박람회", "페스티벌", "이벤트", "체험 프로그램", "문화 프로그램",
            // zh ("节" 단독은 너무 넓어서 제외)
            "活动", "节日", "庆典", "演出", "展览", "博览会", "文化节",
            // vi ("kiện" 단독은 "điều kiện"(조건)에도 들어 있어 "sự kiện" 만)
            "sự kiện", "lễ hội", "triển lãm", "biểu diễn", "hội chợ", "buổi hòa nhạc");

    /** 영어는 단어 경계로 ("prevent" 의 "event" 같은 오탐 방지) */
    private static final Pattern WORDS_EN = Pattern.compile(
            "\\b(events?|festivals?|concerts?|exhibitions?|performances?|fairs?|expos?)\\b",
            Pattern.CASE_INSENSITIVE);

    /**
     * CB-10 카테고리: 질문에 이 단어가 있으면 그 카테고리 행사를 먼저 보여 준다 (EVE-10 카테고리와 같은 값).
     * 행사 질문인지는 {@link #detect} 로 따로 판단하고, 여기서는 순서만 정한다.
     */
    private static final Map<EventCategory, List<String>> CATEGORY_WORDS = Map.of(
            EventCategory.FESTIVAL, List.of("축제", "페스티벌", "节日", "庆典", "文化节", "lễ hội"),
            EventCategory.PERFORMANCE, List.of("공연", "콘서트", "음악회", "연극", "뮤지컬", "演出", "音乐会",
                    "biểu diễn", "buổi hòa nhạc"),
            EventCategory.EXHIBITION, List.of("전시", "展览", "triển lãm"),
            EventCategory.EDUCATION, List.of("교육", "강좌", "강의", "수업", "체험", "课程", "教育", "体验",
                    "lớp học", "khóa học", "trải nghiệm"));

    private static final Map<EventCategory, Pattern> CATEGORY_WORDS_EN = Map.of(
            EventCategory.FESTIVAL, Pattern.compile("\\bfestivals?\\b", Pattern.CASE_INSENSITIVE),
            EventCategory.PERFORMANCE, Pattern.compile("\\b(concerts?|performances?|shows?|musicals?|plays?)\\b",
                    Pattern.CASE_INSENSITIVE),
            EventCategory.EXHIBITION, Pattern.compile("\\b(exhibitions?|exhibits?|galler(y|ies))\\b",
                    Pattern.CASE_INSENSITIVE),
            EventCategory.EDUCATION, Pattern.compile("\\b(class(es)?|courses?|workshops?|lectures?|lessons?)\\b",
                    Pattern.CASE_INSENSITIVE));

    private EventIntentDetector() {
    }

    /** 질문이 가리키는 행사 카테고리 (없으면 빈 집합). "행사"·"event" 는 특정 카테고리로 보지 않는다 */
    public static Set<EventCategory> categories(String question) {
        if (question == null || question.isBlank()) {
            return Set.of();
        }
        String lower = Normalizer.normalize(question, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        Set<EventCategory> found = EnumSet.noneOf(EventCategory.class);
        CATEGORY_WORDS.forEach((category, words) -> {
            if (words.stream().anyMatch(lower::contains)) {
                found.add(category);
            }
        });
        CATEGORY_WORDS_EN.forEach((category, pattern) -> {
            if (pattern.matcher(question).find()) {
                found.add(category);
            }
        });
        return found;
    }

    public static boolean detect(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        // 분해형(NFD)으로 입력된 베트남어 성조 글자도 맞도록 정규화 (ReferralDetector 와 같음)
        String lower = Normalizer.normalize(question, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        return WORDS.stream().anyMatch(lower::contains) || WORDS_EN.matcher(question).find();
    }
}
