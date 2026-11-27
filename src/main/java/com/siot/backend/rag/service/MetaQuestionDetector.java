package com.siot.backend.rag.service;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 이 대화 자체에 대한 질문(메타 질문)을 가려낸다 (ko/en/zh/vi 규칙 기반).
 * 메타 질문은 문서 검색(RAG)을 하지 않고, 대화 기록과 그 답변을 만들 때 저장한 근거로 답한다.
 * <ul>
 *   <li>{@link Type#PAST_QUESTION}: "맨 처음 질문이 뭐야?"</li>
 *   <li>{@link Type#PAST_ANSWER}: "첫 번째 질문에 뭐라고 답했어?"</li>
 *   <li>{@link Type#SOURCE}: "첫 번째 답변의 근거는?", "그 자료 몇 페이지야?"</li>
 *   <li>{@link Type#EXPLAIN}: "왜 그렇게 생각했어?", "종량제 봉투를 언급한 이유는?"</li>
 * </ul>
 * 일반 질문을 메타 질문으로 잘못 보면 답을 못 받으므로, "근거"·"출처" 같은 단어만으로는 메타 질문으로 보지 않고
 * 앞 대화를 가리키는 말(아까, 첫 번째, 답변, 네가 …)이 함께 있거나 질문이 아주 짧을 때만 메타 질문으로 본다.
 */
final class MetaQuestionDetector {

    enum Type { PAST_QUESTION, PAST_ANSWER, SOURCE, EXPLAIN }

    /**
     * @param turn 몇 번째 질문(1부터)을 가리키는지. {@link #PREVIOUS} 면 직전 (근거·설명은 근거가 있는 가장 최근 답변)
     */
    record MetaQuestion(Type type, int turn) {

        static final int PREVIOUS = 0;

        boolean previous() {
            return turn == PREVIOUS;
        }
    }

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    // ---------------------------------------------------------------- 질문 종류

    private static final Pattern EXPLAIN = Pattern.compile(String.join("|",
            // ko
            "왜\\s*(그렇게|그런\\s*식으로|이렇게)\\s*(생각|말|답|대답|판단|알려|안내|설명|얘기|이야기)",
            "(그렇게|그런|이렇게)\\s*(생각|판단|답|대답|말|설명|안내)\\s*(을|를)?\\s*(한|했던|하신)\\s*(이유|까닭|근거)",
            "(언급|말|답|대답|답변|설명|추천|안내)\\s*(을|를)?\\s*(한|했던|하신)\\s*(이유|까닭)",
            "(무슨|어떤)\\s*근거로",
            // en
            "why\\s+(did|do|would)\\s+you\\s+(say|think|answer|mention|suggest|recommend|tell)",
            "what\\s+made\\s+you\\s+(say|think|answer)",
            "how\\s+do\\s+you\\s+know\\s+(that|this)",
            // zh
            "为什么你?(会)?(这么|这样|那样)(说|想|回答|认为|判断)",
            "你为什么(说|认为|这么|这样|那样|会)",
            "(这么|这样|那样)(说|回答)的(理由|原因|依据)",
            // vi
            "(tại sao|vì sao|sao)\\s+(bạn\\s+)?(lại\\s+)?(nói|nghĩ|trả lời|cho rằng)\\s+(như\\s+)?(vậy|thế)"), FLAGS);

    /** 근거·출처. 일반 질문에도 흔한 단어라 앞 대화를 가리키는 말이 함께 있거나 아주 짧을 때만 */
    private static final Pattern SOURCE = Pattern.compile(String.join("|",
            // ko
            "근거", "출처", "참고\\s*(한|하신|했던)", "참조\\s*(한|하신)",
            "어디(에|에서|서)\\s*(나와|나오|나온|적혀|쓰여|써\\s*있|봤|찾았|가져)",
            "몇\\s*(페이지|쪽|page)",
            "(무슨|어떤|어느)\\s*(자료|문서)\\s*(를|을|에서)?\\s*(참고|보고|봤|근거|바탕)",
            // en
            "\\b(sources?|evidence|references?|citations?)\\b",
            "where\\s+(is|was|did|does)\\s+(that|this|it|you)\\b.*\\b(from|get|got|find|found|say|written)\\b",
            "\\b(which|what)\\s+page",
            // zh
            "依据", "根据(是|在|什么)", "出处", "来源", "哪一?页", "第几页", "在哪(里|儿)?(写|看到|提到|找到|查到)",
            // vi
            "nguồn", "căn cứ", "dẫn chứng", "tài liệu nào", "trang\\s+(nào|mấy|bao nhiêu|số mấy)",
            "ở đâu ra", "lấy\\s+(thông tin\\s+)?(ở|từ)\\s+đâu"), FLAGS);

    private static final Pattern PAST_ANSWER = Pattern.compile(String.join("|",
            // ko
            "(뭐라고|무엇이라고|뭐라|뭐라구)\\s*(답|대답|말|했|하셨|알려|설명)",
            "(답변|대답|답)\\s*(은|는|이|이었|이였)?\\s*(뭐|무엇|뭔|어땠|어떻게\\s*했)",
            "어떻게\\s*(답|대답)\\s*했",
            // en
            "what\\s+did\\s+you\\s+(say|answer|reply|tell)",
            "what\\s+(was|is)\\s+your\\s+(\\w+\\s+)?(answer|reply|response)",
            "how\\s+did\\s+you\\s+(answer|reply)",
            // zh
            "你(刚才|之前|上次|最初|一开始)?(是)?(怎么|如何)回答",
            "你(刚才|之前|上次|最初|一开始)?(说|回答)了(什么|啥)",
            "(第\\s*\\S\\s*个|最初|一开始|上一个|刚才|之前)的?(问题的)?(回答|答复|答案)是(什么|啥)",
            // vi
            "bạn\\s+(đã\\s+)?(trả lời|nói)\\s+(gì|thế nào|như thế nào|ra sao)",
            "câu trả lời\\s+(đầu tiên|thứ\\s+\\S+|trước|vừa rồi|lúc nãy|cuối cùng)(\\s+của bạn)?\\s+là gì"), FLAGS);

    private static final Pattern PAST_QUESTION = Pattern.compile(String.join("|",
            // ko
            "(질문|물어본|물어\\s*본|물었던|물어봤던|물은)\\s*(게|것|건|거|내용)?\\s*(이|은|는|가)?\\s*(뭐|무엇|뭔|무슨)",
            "(뭐|무엇|뭘|무엇을)\\s*(물어봤|물어\\s*봤|물었|질문했)",
            // en
            "what\\s+(was|is|were)\\s+my\\s+(\\w+\\s+)?questions?",
            "what\\s+did\\s+i\\s+(ask|say)",
            // zh
            "我(刚才|之前|最初|最开始|一开始|第\\s*\\S\\s*个)?问(了|的)(什么|啥|是什么)",
            "(第\\s*\\S\\s*个|最初|最开始|一开始|上一个|刚才|之前)的?问题是(什么|啥)",
            // vi
            "tôi\\s+(đã\\s+)?hỏi\\s+(gì|cái gì|điều gì)",
            "câu hỏi\\s+(đầu tiên|thứ\\s+\\S+|trước|vừa rồi|lúc nãy|cuối cùng)(\\s+của tôi)?\\s+là gì"), FLAGS);

    /**
     * 앞 대화(질문·답변)를 가리키는 말. "처음"·"이전"·"you" 처럼 일반 질문에도 흔한 말은
     * 질문·답변을 가리킬 때만 ("처음 질문", "이전 답변", "you said") 센다.
     */
    private static final Pattern ANCHOR = Pattern.compile(String.join("|",
            // ko
            "네가", "니가", "당신이", "챗봇", "아까", "방금", "조금\\s*전", "바로\\s*전", "직전", "번째",
            "(맨|제일|가장)?\\s*처음(에|에\\s*한|\\s*한|\\s*했던)?\\s*(질문|답|대답|물어|말|얘기)",
            "첫\\s*(질문|답|대답|물음)", "(이전|마지막|앞의|위의)\\s*(질문|답|대답|말|내용|설명)",
            "(앞|위)(에서|서)\\s*(말|얘기|이야기|알려|설명|답|대답|한)",
            "그\\s*(답|대답|자료|문서|내용|말|거|것|설명|정보|페이지|쪽)", "그거", "그것", "그건",
            "이\\s*(답변|대답|자료|내용)", "답변", "대답", "알려\\s*준", "말한", "말했", "설명한", "해\\s*준",
            // en
            "\\b(your|answer|above)\\b",
            "\\b(first|second|third|fourth|fifth|\\d{1,2}(st|nd|rd|th)|last|previous|earlier)\\s+(answer|reply|response|question)",
            "\\byou\\s+(said|say|mentioned|told|answered|used|get|got|find|found|gave|wrote|read|see|saw|just)\\b",
            "\\b(of|for)\\s+(that|this|it)\\s*[?.!]*$", "^\\s*(and\\s+)?(that|this|it)\\b",
            // zh
            "你(说|讲|回答|提到|给|刚才|之前)", "刚才", "之前", "上面", "上一个", "最初", "一开始", "第\\s*\\S\\s*个",
            "最后", "回答", "答案",
            // vi
            "bạn\\s+(vừa\\s+|đã\\s+)?(nói|trả lời|đưa|dùng|lấy)", "câu trả lời", "vừa\\s+(rồi|nói|trả lời)",
            "lúc nãy", "trước đó", "đầu tiên", "thứ\\s+(nhất|hai|ba|tư|năm|\\d+)", "cuối cùng"), FLAGS);

    /** 질문한 사람 자신·챗봇을 가리키는 말. 지난 질문·답변 내용을 물을 때만 앞 대화를 가리키는 말로 센다 */
    private static final Pattern SPEAKER = Pattern.compile(String.join("|",
            "내가", "제가", "나는", "저는", "\\b(i|my|you)\\b", "我", "你", "\\btôi\\b", "\\bbạn\\b"), FLAGS);

    /** 글자 수(공백·문장부호 제외)가 이 이하면 "근거는?" 같은 짧은 되묻기로 본다 */
    private static final int SHORT_QUESTION = 8;

    // ---------------------------------------------------------------- 몇 번째

    private static final Map<String, Integer> NUMBER_WORDS = Map.ofEntries(
            // ko
            Map.entry("두", 2), Map.entry("세", 3), Map.entry("네", 4), Map.entry("다섯", 5), Map.entry("여섯", 6),
            Map.entry("일곱", 7), Map.entry("여덟", 8), Map.entry("아홉", 9), Map.entry("열", 10),
            Map.entry("둘", 2), Map.entry("셋", 3), Map.entry("넷", 4),
            // en
            Map.entry("second", 2), Map.entry("third", 3), Map.entry("fourth", 4), Map.entry("fifth", 5),
            // zh
            Map.entry("一", 1), Map.entry("二", 2), Map.entry("两", 2), Map.entry("三", 3), Map.entry("四", 4),
            Map.entry("五", 5), Map.entry("六", 6), Map.entry("七", 7), Map.entry("八", 8), Map.entry("九", 9),
            Map.entry("十", 10),
            // vi
            Map.entry("nhất", 1), Map.entry("hai", 2), Map.entry("ba", 3), Map.entry("tư", 4), Map.entry("năm", 5),
            Map.entry("sáu", 6), Map.entry("bảy", 7), Map.entry("tám", 8), Map.entry("chín", 9),
            Map.entry("mười", 10));

    private static final Pattern NTH = Pattern.compile(String.join("|",
            "(\\d{1,2})\\s*번째", "(두|세|네|다섯|여섯|일곱|여덟|아홉|열)\\s*번째", "(둘|셋|넷)째",
            "\\b(\\d{1,2})(?:st|nd|rd|th)\\b", "\\b(second|third|fourth|fifth)\\b",
            "第\\s*(\\d{1,2}|[一二两三四五六七八九十])",
            "thứ\\s+(\\d{1,2}|nhất|hai|ba|tư|năm|sáu|bảy|tám|chín|mười)"), FLAGS);

    private static final Pattern FIRST = Pattern.compile(String.join("|",
            "처음", "첫", "\\bfirst\\b", "最初", "最开始", "一开始", "đầu tiên"), FLAGS);

    private MetaQuestionDetector() {
    }

    /** @param question 개인정보를 가린 질문 */
    static Optional<MetaQuestion> detect(String question) {
        if (question == null || question.isBlank()) {
            return Optional.empty();
        }
        String text = question.toLowerCase(Locale.ROOT);
        Type type = type(text);
        return type == null ? Optional.empty() : Optional.of(new MetaQuestion(type, turn(text)));
    }

    private static Type type(String text) {
        boolean anchored = ANCHOR.matcher(text).find();
        // 근거·설명을 먼저 본다 ("첫 번째 답변의 근거는?" 은 답변 내용이 아니라 근거를 묻는 것)
        if (EXPLAIN.matcher(text).find()) {
            return Type.EXPLAIN;
        }
        if (SOURCE.matcher(text).find() && (anchored || isShort(text))) {
            return Type.SOURCE;
        }
        boolean aboutTurn = anchored || SPEAKER.matcher(text).find();
        if (PAST_ANSWER.matcher(text).find() && aboutTurn) {
            return Type.PAST_ANSWER;
        }
        if (PAST_QUESTION.matcher(text).find() && aboutTurn) {
            return Type.PAST_QUESTION;
        }
        return null;
    }

    private static boolean isShort(String text) {
        return text.codePoints().filter(Character::isLetterOrDigit).count() <= SHORT_QUESTION;
    }

    /** "두 번째" → 2, "처음" → 1, 그 외 (아까·마지막·직전·말이 없음) → 직전 */
    static int turn(String text) {
        Matcher nth = NTH.matcher(text);
        if (nth.find()) {
            for (int g = 1; g <= nth.groupCount(); g++) {
                String value = nth.group(g);
                if (value != null) {
                    Integer n = value.chars().allMatch(Character::isDigit)
                            ? Integer.valueOf(value) : NUMBER_WORDS.get(value);
                    if (n != null && n > 0) {
                        return n;
                    }
                }
            }
        }
        return FIRST.matcher(text).find() ? 1 : MetaQuestion.PREVIOUS;
    }
}
