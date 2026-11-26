package com.siot.backend.rag.service;

import com.siot.backend.rag.domain.ReferralTopic;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * CB-11: 비자 발급 가능 여부, 법률·의료·세금·노동 판단을 묻는 질문을 감지한다.
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
            "有资格", "批准", "拒签", "被拒", "我的情况",
            // vi
            "đủ điều kiện", "bị từ chối");

    private static final List<String> VISA_WEAK_CUES = List.of(
            // ko ("가능" 은 "가능할까·가능한가요" 도 포함)
            "가능", "될까", "되나요", "될 수", "받을 수", "할 수 있", "있을까", "있나요", "안 되", "안되",
            // zh
            "可以", "能否", "能吗", "能不能", "会不会",
            // vi
            "có được", "được không", "có thể", "có bị");

    /** zh: "能延长吗·能换永住权吗" 처럼 能 과 吗 사이에 동사가 들어가는 표현 */
    private static final Pattern VISA_WEAK_CUES_ZH = Pattern.compile("能[^，,。？?！!\\s]{1,8}吗");

    private static final Pattern VISA_STRONG_CUES_EN = Pattern.compile(
            "\\b(am i|eligib\\w*|qualify|approved|denied|rejected|refused)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern VISA_WEAK_CUES_EN = Pattern.compile(
            "\\b((can|could|will|would) (i|my)|possible)\\b", Pattern.CASE_INSENSITIVE);

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
            "이길 수", "이길까", "이기나요", "승소", "패소",
            // ko: 계약서를 대신 검토해 달라는 질문
            "서명해도", "사인해도", "도장 찍어도", "계약해도 괜찮", "계약해도 될까", "계약해도 되나",
            // zh
            "违法", "合法", "犯法", "起诉", "诉讼", "律师", "处罚", "判刑", "法院", "赔偿", "能赢", "会赢", "胜诉",
            "签字可以", "签字没问题", "签了没问题", "可以签吗", "能签吗", "直接签",
            // vi ("kiện" 은 "điều kiện"(조건)·"sự kiện"(행사)에 들어 있어 단독으로는 제외)
            "hợp pháp", "phạm pháp", "khởi kiện", "bị kiện", "kiện tụng", "luật sư", "xử phạt", "tòa án", "bồi thường",
            "nếu kiện", "đi kiện", "thắng kiện", "có thắng không", "kiện ra tòa");

    private static final Pattern LEGAL_EN = Pattern.compile(
            "\\b(illegal|legally|is it legal|lawsuit|sue|sued|lawyer|attorney|court|punish(ed|ment)?|"
                    + "criminal record|prosecut\\w*|liable|liability|"
                    + "(okay|ok|safe|fine|alright) (for me )?to sign|(should|can) i (just )?sign (this|the|it|my|a))\\b",
            Pattern.CASE_INSENSITIVE);

    /** vi: "ký hợp đồng ... có được không" (계약서에 서명해도 되는지). "đăng ký hợp đồng"(계약 등록)은 제외 */
    private static final Pattern LEGAL_SIGN_VI = Pattern.compile("(?<!đăng )ký hợp đồng.*(được không|có nên|có sao)");

    // ---------------------------------------------------------------- MEDICAL

    private static final List<String> MEDICAL_PHRASES = List.of(
            // ko
            "증상", "진단", "아파요", "아픈데", "아프고", "아파서", "열이 나", "열이나", "기침", "두통", "복통",
            "약을 먹", "약 먹", "복용", "부작용", "처방", "무슨 병", "병인가", "병인지", "수술해야", "치료해야", "임신",
            // zh
            "症状", "诊断", "感冒药", "退烧药", "止痛药", "这个药", "疼", "痛", "发烧", "咳嗽", "吃药", "药物", "副作用", "处方", "怀孕", "生病",
            // vi
            "triệu chứng", "chẩn đoán", "bị đau", "đau đầu", "đau bụng", "bị sốt", "uống thuốc", "tác dụng phụ",
            "đơn thuốc", "mang thai", "bị bệnh");

    private static final Pattern MEDICAL_EN = Pattern.compile(
            "\\b(symptoms?|diagnos\\w*|sick|pain|fever|cough|headache|medicine|medication|side effects?|"
                    + "prescription|dosage|pregnan\\w*)\\b",
            Pattern.CASE_INSENSITIVE);

    // ---------------------------------------------------------------- TAX: 세금 단어 + "내 경우·더 유리한지·얼마 받는지"
    // 제도·일정 질문("연말정산은 언제 하나요?", "19% 단일세율을 적용받을 수 있나요?")은 제외한다.

    private static final List<String> TAX_TOPICS = List.of(
            // ko
            "세금", "세율", "세액", "연말정산", "환급", "소득세", "원천징수", "공제", "납세",
            // zh
            "税", "年终结算", "退税",
            // vi
            "thuế", "quyết toán", "hoàn thuế");

    private static final Pattern TAX_TOPICS_EN = Pattern.compile(
            "\\b(tax\\w*|refunds?|year-end settlement|deductions?|flat rate)\\b", Pattern.CASE_INSENSITIVE);

    private static final List<String> TAX_JUDGMENT_CUES = List.of(
            // ko
            "유리", "불리", "이득", "제 경우", "내 경우", "제 상황", "제가 받", "얼마나 받", "얼마 받", "얼마나 돌려",
            "얼마 돌려", "얼마나 나올", "얼마 나올",
            // zh
            "对我来说", "我的情况", "有利", "划算", "退多少", "能退",
            // vi
            "đối với tôi", "trường hợp của tôi", "có lợi", "hoàn bao nhiêu", "được bao nhiêu");

    private static final Pattern TAX_JUDGMENT_CUES_EN = Pattern.compile(
            "\\b(for me|in my case|my situation|which is better|better for me|more (beneficial|advantageous)|"
                    + "(will|would|can|could|do) i (get|receive))\\b",
            Pattern.CASE_INSENSITIVE);

    // ---------------------------------------------------------------- LABOR: 해고·임금 체불 (분쟁이라 단어만으로 감지)

    private static final List<String> LABOR_PHRASES = List.of(
            // ko
            "해고", "나가라고", "그만두라고", "임금체불", "임금 체불", "체불", "월급을 안", "월급을 못", "월급 안 ", "월급 못 ",
            "월급이 밀", "급여를 안", "급여를 못", "임금을 안", "임금을 못", "퇴직금을 안", "퇴직금을 못", "퇴직금 안 ",
            "퇴직금 못 ", "최저임금보다 적", "최저임금도 안",
            // zh
            "解雇", "开除", "辞退", "拖欠工资", "欠薪", "不给工资", "没发工资", "没给工资", "克扣工资", "不给退职金",
            // vi
            "sa thải", "đuổi việc", "nợ lương", "không trả lương", "chưa trả lương", "quỵt lương", "trừ lương");

    private static final Pattern LABOR_EN = Pattern.compile(
            "\\b(fired|dismiss(al|ed)|laid off|unpaid (wages?|salary)|wage theft|"
                    + "(not|never|didn'?t|hasn'?t|won'?t|doesn'?t) (been )?pa(id|y)(ing)? (me|my)|"
                    + "withh[eo]ld\\w* (my )?(wages?|salary|pay))\\b",
            Pattern.CASE_INSENSITIVE);

    // ---------------------------------------------------------------- WORK_INJURY: 산업재해

    private static final List<String> WORK_INJURY_PHRASES = List.of(
            // ko
            "산재", "산업재해", "일하다가 다", "일하다 다", "일하는 중에 다", "작업 중 다", "작업중 다", "작업하다가 다",
            "근무 중 다", "근무중 다", "업무 중 다", "회사에서 다쳤", "공장에서 다쳤",
            // zh
            "工伤", "工作时受伤", "工作中受伤", "上班时受伤", "干活时受伤",
            // vi
            "tai nạn lao động", "bị thương khi làm việc", "bị thương trong lúc làm việc", "bị thương lúc làm việc");

    private static final Pattern WORK_INJURY_EN = Pattern.compile(
            "\\b(industrial accidents?|work(place)?[- ]?related injur\\w*|workplace injur\\w*|work injur\\w*|"
                    + "workers'? comp\\w*|(hurt|injured) (myself )?(at|while|during) (work|my work|working))\\b",
            Pattern.CASE_INSENSITIVE);

    private ReferralDetector() {
    }

    /** @return 감지한 종류 (VISA, LEGAL, MEDICAL, TAX, LABOR, WORK_INJURY 순서, 없으면 빈 목록) */
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
        if (containsAny(lower, LEGAL_PHRASES) || LEGAL_EN.matcher(normalized).find()
                || LEGAL_SIGN_VI.matcher(lower).find()) {
            topics.add(ReferralTopic.LEGAL);
        }
        if (containsAny(lower, MEDICAL_PHRASES) || MEDICAL_EN.matcher(normalized).find()) {
            topics.add(ReferralTopic.MEDICAL);
        }
        boolean taxTopic = containsAny(lower, TAX_TOPICS) || TAX_TOPICS_EN.matcher(normalized).find();
        if (taxTopic && (containsAny(lower, TAX_JUDGMENT_CUES) || TAX_JUDGMENT_CUES_EN.matcher(normalized).find())) {
            topics.add(ReferralTopic.TAX);
        }
        if (containsAny(lower, LABOR_PHRASES) || LABOR_EN.matcher(normalized).find()) {
            topics.add(ReferralTopic.LABOR);
        }
        if (containsAny(lower, WORK_INJURY_PHRASES) || WORK_INJURY_EN.matcher(normalized).find()) {
            topics.add(ReferralTopic.WORK_INJURY);
        }
        return List.copyOf(topics);
    }

    private static boolean isVisaJudgment(String lower, String normalized) {
        if (containsAny(lower, VISA_STRONG_CUES) || VISA_STRONG_CUES_EN.matcher(normalized).find()) {
            return true;
        }
        boolean weakCue = containsAny(lower, VISA_WEAK_CUES) || VISA_WEAK_CUES_EN.matcher(normalized).find()
                || VISA_WEAK_CUES_ZH.matcher(normalized).find();
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
