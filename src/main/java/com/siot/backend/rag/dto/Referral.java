package com.siot.backend.rag.dto;

import com.siot.backend.rag.domain.ReferralTopic;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * CB-11: 비자 발급 가능 여부·법률·의료·세금·노동 판단 질문에 답변과 함께 보여 줄 고정 안내와 담당 기관.
 *
 * @param notice   "개인 상황은 판단할 수 없다"는 고정 문구 (대화 언어)
 * @param contacts 담당 기관 연락처 (질문 종류마다 1개)
 */
@Schema(description = "CB-11 담당 기관 안내 (판단하지 않고 기관으로 연결)")
public record Referral(
        @Schema(description = "'개인 상황은 판단할 수 없다'는 고정 문구 (대화 언어)") String notice,
        @Schema(description = "담당 기관 (질문 종류마다 1개, VISA → LEGAL → MEDICAL → TAX → LABOR → WORK_INJURY 순)") List<Contact> contacts) {

    /** @param topic VISA / LEGAL / MEDICAL / TAX / LABOR / WORK_INJURY */
    @Schema(name = "ReferralContact", description = "CB-11 담당 기관 연락처")
    public record Contact(
            @Schema(description = "질문 종류", allowableValues = {"VISA", "LEGAL", "MEDICAL", "TAX", "LABOR", "WORK_INJURY"}, example = "VISA") String topic,
            @Schema(description = "전화번호", example = "1345") String number,
            @Schema(description = "대화 언어의 기관 이름", example = "외국인종합안내센터 (비자·체류)") String label) {
    }

    private static final Map<String, String> NOTICE = Map.of(
            "ko", "개인 상황에 대한 판단(비자 발급 가능 여부, 법률·의료·세금·노동 문제)은 해 드릴 수 없어요. 정확한 답은 아래 기관에 확인해 주세요.",
            "en", "I can't judge your individual situation (visa eligibility, legal, medical, tax or labor matters). Please check with the office below for an accurate answer.",
            "zh", "无法对个人情况（能否办理签证、法律、医疗、税务或劳动问题）作出判断。请向以下机构确认准确答复。",
            "vi", "Tôi không thể đánh giá trường hợp cá nhân (khả năng được cấp thị thực, vấn đề pháp lý, y tế, thuế hoặc lao động). Vui lòng hỏi cơ quan dưới đây để có câu trả lời chính xác.");

    private static final Map<ReferralTopic, String> NUMBERS = Map.of(
            ReferralTopic.VISA, "1345",
            ReferralTopic.LEGAL, "132",
            ReferralTopic.MEDICAL, "119",
            // 시흥시 외국인주민 생활안내 37쪽
            ReferralTopic.TAX, "126",
            ReferralTopic.LABOR, "1350",
            ReferralTopic.WORK_INJURY, "1588-0075");

    private static final Map<ReferralTopic, Map<String, String>> LABELS = Map.of(
            ReferralTopic.VISA, Map.of(
                    "ko", "외국인종합안내센터 (비자·체류)",
                    "en", "Immigration Contact Center (visa & stay)",
                    "zh", "外国人综合服务中心（签证·居留）",
                    "vi", "Trung tâm hỗ trợ người nước ngoài (thị thực & lưu trú)"),
            ReferralTopic.LEGAL, Map.of(
                    "ko", "대한법률구조공단 (무료 법률 상담)",
                    "en", "Korea Legal Aid Corporation (free legal advice)",
                    "zh", "大韩法律救助公团（免费法律咨询）",
                    "vi", "Tổng công ty Trợ giúp pháp lý Hàn Quốc (tư vấn pháp lý miễn phí)"),
            ReferralTopic.MEDICAL, Map.of(
                    "ko", "119 구급상황관리센터 (병원 안내·의료 상담)",
                    "en", "119 Emergency Medical Center (hospital info & medical advice)",
                    "zh", "119急救状况管理中心（医院指引·医疗咨询）",
                    "vi", "Trung tâm cấp cứu 119 (hướng dẫn bệnh viện & tư vấn y tế)"),
            ReferralTopic.TAX, Map.of(
                    "ko", "국세청 국세상담센터 (세금·연말정산)",
                    "en", "National Tax Service Call Center (tax & year-end settlement)",
                    "zh", "国税厅国税咨询中心（税务·年终结算）",
                    "vi", "Trung tâm tư vấn thuế quốc gia (thuế & quyết toán cuối năm)"),
            ReferralTopic.LABOR, Map.of(
                    "ko", "고용노동부 고객상담센터 (해고·임금·근로조건)",
                    "en", "Ministry of Employment and Labor Call Center (dismissal, wages & working conditions)",
                    "zh", "雇佣劳动部客户咨询中心（解雇·工资·劳动条件）",
                    "vi", "Trung tâm tư vấn Bộ Việc làm và Lao động (sa thải, tiền lương & điều kiện làm việc)"),
            ReferralTopic.WORK_INJURY, Map.of(
                    "ko", "근로복지공단 (산업재해)",
                    "en", "Korea Workers' Compensation & Welfare Service (industrial accidents)",
                    "zh", "勤劳福祉公团（工伤）",
                    "vi", "Cơ quan Phúc lợi và Bồi thường Lao động Hàn Quốc (tai nạn lao động)"));

    /** @return 감지한 종류가 없으면 null */
    public static Referral of(List<ReferralTopic> topics, String language) {
        if (topics.isEmpty()) {
            return null;
        }
        String lang = NOTICE.containsKey(language) ? language : "ko";
        List<Contact> contacts = topics.stream()
                .map(t -> new Contact(t.name(), NUMBERS.get(t), LABELS.get(t).get(lang)))
                .toList();
        return new Referral(NOTICE.get(lang), contacts);
    }
}
