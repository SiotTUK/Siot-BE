package com.siot.backend.rag.service;

import com.siot.backend.rag.domain.ReferralTopic;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * CB-11: 비자 발급 가능 여부, 법률·의료 판단을 묻는 질문을 감지한다.
 * 감지되면 답변과 함께 "판단할 수 없다"는 고정 문구와 담당 기관 연락처를 보여 준다 (Gemini 지시문에만 의존하지 않음).
 * CB-09 와 같이 놓치는 것보다 한 번 더 보여 주는 편이 낫기 때문에 넓게 잡는다.
 */
public final class ReferralDetector {

    // ---------------------------------------------------------------- VISA: 체류 관련 단어 + "가능한지" 묻는 표현
    // 서류·절차 질문("어디서 신청할 수 있나요?", "언제 하면 될까요?")은 제외한다 (CB-11 #2).
    //  - 판단 단어(STRONG): 거절·승인·자격처럼 결과를 묻는 단어. 있으면 항상 감지 ("거절될까요?")
    //  - 가능 여부 표현(WEAK): "가능할까요·될까요·할 수 있나요·can I" 는 절차 질문에도 쓰여서, 절차 단어가 없을 때만 감지

    /** "연장"·"延期"·"gia hạn"·"extension" 단독은 운전면허·계약 연장 등에도 쓰여서 넣지 않는다 (비자·체류 단어와 함께 나올 때만) */
    private static final List<String> VISA_TOPICS = List.of(
            // ko
            "비자", "체류자격", "체류 자격", "체류기간", "체류 기간", "체류 연장", "체류연장", "자격 변경", "영주권", "귀화",
            "국적", "재입국", "초청", "불법체류", "불법 체류", "출국명령", "강제퇴거", "추방",
            // zh
            "签证", "居留", "滞留", "永住", "入籍", "国籍", "再入境", "遣返",
            // vi
            "thị thực", "lưu trú", "thường trú", "quốc tịch", "tái nhập cảnh", "trục xuất");

    private static final Pattern VISA_TOPICS_EN = Pattern.compile(
            "\\b(visas?|residence status|stay permit|extend(ing)? my stay|stay extension|permanent residen\\w*|"
                    + "naturali[sz]\\w*|citizenship|re-?entry|overstay\\w*|deport\\w*)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final List<String> VISA_STRONG_CUES = List.of(
            // ko
            "가능성", "거절", "불허", "떨어", "나올까", "승인", "자격이 되", "자격이 있",
            // zh
            "有资格", "批准", "拒签", "被拒",
            // vi
            "đủ điều kiện", "bị từ chối");

    private static final List<String> VISA_WEAK_CUES = List.of(
            // ko ("가능" 은 "가능할까·가능한가요" 도 포함)
            "가능", "될까", "되나요", "될 수", "받을 수", "할 수 있", "있을까", "있나요", "안 되", "안되",
            // zh
            "可以", "能否", "能吗", "能不能", "会不会",
            // vi
            "có được", "được không", "có thể", "có bị");

    private static final Pattern VISA_STRONG_CUES_EN = Pattern.compile(
            "\\b(am i|eligib\\w*|qualify|approved|denied|rejected|refused)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern VISA_WEAK_CUES_EN = Pattern.compile(
            "\\b(can i|could i|will i|would i|possible)\\b", Pattern.CASE_INSENSITIVE);

    /** 서류·절차를 묻는 단어 */
    private static final List<String> PROCEDURE_WORDS = List.of(
            // ko
            "어디서", "어디에서", "어디로", "어떻게", "방법", "서류", "준비물", "온라인", "인터넷으로", "언제", "수수료",
            "절차", "신청서", "예약",
            // zh
            "哪里", "在哪", "怎么", "如何", "材料", "手续", "网上", "什么时候", "费用", "预约",
            // vi
            "ở đâu", "thế nào", "giấy tờ", "hồ sơ", "thủ tục", "trực tuyến", "khi nào", "lệ phí", "đặt lịch");

    private static final Pattern PROCEDURE_WORDS_EN = Pattern.compile(
            "\\b(where|how|online|documents?|paperwork|when|fees?|procedures?|steps|appointment)\\b",
            Pattern.CASE_INSENSITIVE);

    // ---------------------------------------------------------------- LEGAL

    private static final List<String> LEGAL_PHRASES = List.of(
            // ko ("불법체류" 같은 명사는 제외하고 판단을 묻는 표현만)
            "불법인가", "불법이에요", "불법이야", "불법일까", "불법인지", "합법", "위법", "고소", "고발", "소송", "변호사",
            "처벌", "벌금을 내야", "벌금 받", "재판", "형사", "민사", "무효인가", "무효인지", "손해배상", "법적으로", "법적 책임",
            // zh
            "违法", "合法", "犯法", "起诉", "诉讼", "律师", "处罚", "判刑", "法院", "赔偿",
            // vi ("kiện" 은 "điều kiện"(조건)·"sự kiện"(행사)에 들어 있어 제외)
            "hợp pháp", "phạm pháp", "khởi kiện", "bị kiện", "kiện tụng", "luật sư", "xử phạt", "tòa án", "bồi thường");

    private static final Pattern LEGAL_EN = Pattern.compile(
            "\\b(illegal|legally|is it legal|lawsuit|sue|sued|lawyer|attorney|court|punish(ed|ment)?|"
                    + "criminal record|prosecut\\w*|liable|liability)\\b",
            Pattern.CASE_INSENSITIVE);

    // ---------------------------------------------------------------- MEDICAL

    private static final List<String> MEDICAL_PHRASES = List.of(
            // ko
            "증상", "진단", "아파요", "아픈데", "아프고", "아파서", "열이 나", "열이나", "기침", "두통", "복통",
            "약을 먹", "약 먹", "복용", "부작용", "처방", "무슨 병", "병인가", "병인지", "수술해야", "치료해야", "임신",
            // zh
            "症状", "诊断", "疼", "痛", "发烧", "咳嗽", "吃药", "药物", "副作用", "处方", "怀孕", "生病",
            // vi
            "triệu chứng", "chẩn đoán", "bị đau", "đau đầu", "đau bụng", "bị sốt", "uống thuốc", "tác dụng phụ",
            "đơn thuốc", "mang thai", "bị bệnh");

    private static final Pattern MEDICAL_EN = Pattern.compile(
            "\\b(symptoms?|diagnos\\w*|sick|pain|fever|cough|headache|medicine|medication|side effects?|"
                    + "prescription|dosage|pregnan\\w*)\\b",
            Pattern.CASE_INSENSITIVE);

    private ReferralDetector() {
    }

    /** @return 감지한 종류 (VISA, LEGAL, MEDICAL 순서, 없으면 빈 목록) */
    public static List<ReferralTopic> detect(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFC);
        String lower = normalized.toLowerCase(Locale.ROOT);
        List<ReferralTopic> topics = new ArrayList<>();
        boolean visaTopic = containsAny(lower, VISA_TOPICS) || VISA_TOPICS_EN.matcher(normalized).find();
        if (visaTopic && isVisaJudgment(lower, normalized)) {
            topics.add(ReferralTopic.VISA);
        }
        if (containsAny(lower, LEGAL_PHRASES) || LEGAL_EN.matcher(normalized).find()) {
            topics.add(ReferralTopic.LEGAL);
        }
        if (containsAny(lower, MEDICAL_PHRASES) || MEDICAL_EN.matcher(normalized).find()) {
            topics.add(ReferralTopic.MEDICAL);
        }
        return List.copyOf(topics);
    }

    private static boolean isVisaJudgment(String lower, String normalized) {
        if (containsAny(lower, VISA_STRONG_CUES) || VISA_STRONG_CUES_EN.matcher(normalized).find()) {
            return true;
        }
        boolean weakCue = containsAny(lower, VISA_WEAK_CUES) || VISA_WEAK_CUES_EN.matcher(normalized).find();
        boolean procedure = containsAny(lower, PROCEDURE_WORDS) || PROCEDURE_WORDS_EN.matcher(normalized).find();
        return weakCue && !procedure;
    }

    private static boolean containsAny(String lower, List<String> phrases) {
        for (String phrase : phrases) {
            if (lower.contains(phrase)) {
                return true;
            }
        }
        return false;
    }
}
