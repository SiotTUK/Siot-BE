package com.siot.backend.rag.service;

import java.util.List;
import java.util.Locale;
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

    private EventIntentDetector() {
    }

    public static boolean detect(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        String lower = question.toLowerCase(Locale.ROOT);
        return WORDS.stream().anyMatch(lower::contains) || WORDS_EN.matcher(question).find();
    }
}
