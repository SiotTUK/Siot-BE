package com.siot.backend.rag.service;

import java.util.Map;

/** 챗봇이 직접 만드는 안내 문구 (ko/en/zh/vi) */
final class ChatbotMessages {

    /** CB-12: 자료에서 답을 찾지 못함 */
    private static final Map<String, String> NO_EVIDENCE = Map.of(
            "ko", "자료에서 찾지 못했어요. 외국인종합안내센터(☎1345)나 시흥시 외국인주민과에 문의해 주세요.",
            "en", "I couldn't find this in our materials. Please contact the Immigration Contact Center (☎1345) or the Foreign Residents Division of Siheung City.",
            "zh", "资料中没有找到相关内容。请咨询外国人综合服务中心（☎1345）或始兴市外国人居民科。",
            "vi", "Tôi không tìm thấy nội dung này trong tài liệu. Vui lòng liên hệ Trung tâm hỗ trợ người nước ngoài (☎1345) hoặc Phòng Cư dân nước ngoài thành phố Siheung.");

    /** CB-02: Gemini 오류 */
    private static final Map<String, String> UNAVAILABLE = Map.of(
            "ko", "지금은 답변을 만들 수 없어요. 잠시 후 다시 시도하거나 외국인종합안내센터(☎1345)에 문의해 주세요.",
            "en", "I can't answer right now. Please try again later or contact the Immigration Contact Center (☎1345).",
            "zh", "现在无法生成回答。请稍后再试，或咨询外国人综合服务中心（☎1345）。",
            "vi", "Hiện tại tôi không thể trả lời. Vui lòng thử lại sau hoặc liên hệ Trung tâm hỗ trợ người nước ngoài (☎1345).");

    private ChatbotMessages() {
    }

    static String noEvidence(String language) {
        return NO_EVIDENCE.getOrDefault(language, NO_EVIDENCE.get("ko"));
    }

    static String unavailable(String language) {
        return UNAVAILABLE.getOrDefault(language, UNAVAILABLE.get("ko"));
    }
}
