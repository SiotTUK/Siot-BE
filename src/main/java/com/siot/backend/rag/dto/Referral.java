package com.siot.backend.rag.dto;

import com.siot.backend.rag.domain.ReferralTopic;

import java.util.List;
import java.util.Map;

/**
 * CB-11: 비자 발급 가능 여부·법률·의료 판단 질문에 답변과 함께 보여 줄 고정 안내와 담당 기관.
 *
 * @param notice   "개인 상황은 판단할 수 없다"는 고정 문구 (대화 언어)
 * @param contacts 담당 기관 연락처 (질문 종류마다 1개)
 */
public record Referral(String notice, List<Contact> contacts) {

    /** @param topic VISA / LEGAL / MEDICAL */
    public record Contact(String topic, String number, String label) {
    }

    private static final Map<String, String> NOTICE = Map.of(
            "ko", "개인 상황에 대한 판단(비자 발급 가능 여부, 법률·의료 판단)은 해 드릴 수 없어요. 정확한 답은 아래 기관에 확인해 주세요.",
            "en", "I can't judge your individual situation (visa eligibility, legal or medical matters). Please check with the office below for an accurate answer.",
            "zh", "无法对个人情况（能否办理签证、法律或医疗问题）作出判断。请向以下机构确认准确答复。",
            "vi", "Tôi không thể đánh giá trường hợp cá nhân (khả năng được cấp thị thực, vấn đề pháp lý hoặc y tế). Vui lòng hỏi cơ quan dưới đây để có câu trả lời chính xác.");

    private static final Map<ReferralTopic, String> NUMBERS = Map.of(
            ReferralTopic.VISA, "1345",
            ReferralTopic.LEGAL, "132",
            ReferralTopic.MEDICAL, "119");

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
                    "vi", "Trung tâm cấp cứu 119 (hướng dẫn bệnh viện & tư vấn y tế)"));

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
