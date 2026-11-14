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

    private static final List<String> VISA_TOPICS = List.of(
            // ko
            "비자", "체류자격", "체류 자격", "체류기간", "체류 기간", "연장", "자격 변경", "영주권", "귀화", "국적",
            "재입국", "초청", "불법체류", "불법 체류", "출국명령", "강제퇴거", "추방",
            // zh
            "签证", "居留", "延期", "滞留", "永住", "入籍", "国籍", "再入境", "遣返",
            // vi
            "thị thực", "lưu trú", "gia hạn", "thường trú", "quốc tịch", "tái nhập cảnh", "trục xuất");

    private static final Pattern VISA_TOPICS_EN = Pattern.compile(
            "\\b(visas?|residence status|stay permit|extend(ing)? my stay|extension|permanent residen\\w*|"
                    + "naturali[sz]\\w*|citizenship|re-?entry|overstay\\w*|deport\\w*)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final List<String> VISA_CUES = List.of(
            // ko
            "가능", "될까", "되나요", "될 수", "받을 수", "할 수 있", "있을까", "있나요", "안 되", "안되", "거절", "불허",
            "떨어", "나올까", "승인",
            // zh
            "可以", "能不能", "能否", "能吗", "会不会", "有资格", "批准", "拒签", "被拒",
            // vi
            "có được", "được không", "có thể", "đủ điều kiện", "bị từ chối", "có bị");

    private static final Pattern VISA_CUES_EN = Pattern.compile(
            "\\b(can i|could i|will i|would i|am i|eligib\\w*|possible|qualify|approved|denied|rejected|refused)\\b",
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
        if (visaTopic && (containsAny(lower, VISA_CUES) || VISA_CUES_EN.matcher(normalized).find())) {
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

    private static boolean containsAny(String lower, List<String> phrases) {
        for (String phrase : phrases) {
            if (lower.contains(phrase)) {
                return true;
            }
        }
        return false;
    }
}
