package com.siot.backend.rag.service;

import com.siot.backend.member.domain.UserType;

import java.util.List;
import java.util.Map;

/**
 * CB-08: 사용자 유형별 자주 묻는 질문 3개.
 * '처음 왔어요' 는 외국인등록·체류지 변경신고·쓰레기 배출, '살고 있어요' 는 주민세·체류기간 연장·건강보험.
 */
final class ChatSuggestions {

    private static final Map<String, List<String>> NEW = Map.of(
            "ko", List.of("외국인등록은 어떻게 하나요?", "이사하면 체류지 변경신고는 어떻게 하나요?", "쓰레기는 어떻게 버리나요?"),
            "en", List.of("How do I register as a foreign resident?", "How do I report a change of address after moving?", "How do I throw away trash?"),
            "zh", List.of("怎么办理外国人登录？", "搬家后怎么申报变更居住地？", "垃圾怎么扔？"),
            "vi", List.of("Đăng ký người nước ngoài như thế nào?", "Chuyển nhà thì khai báo thay đổi nơi cư trú thế nào?", "Vứt rác như thế nào?"));

    private static final Map<String, List<String>> LIVING = Map.of(
            "ko", List.of("주민세는 언제 내나요?", "체류기간 연장은 언제 신청하나요?", "건강보험료는 어떻게 내나요?"),
            "en", List.of("When do I pay the resident tax?", "When should I apply to extend my stay?", "How do I pay health insurance premiums?"),
            "zh", List.of("居民税什么时候交？", "什么时候申请延长滞留期间？", "健康保险费怎么交？"),
            "vi", List.of("Khi nào phải nộp thuế cư trú?", "Khi nào nên xin gia hạn thời gian lưu trú?", "Đóng phí bảo hiểm y tế như thế nào?"));

    /** 온보딩 전 */
    private static final Map<String, List<String>> GENERAL = Map.of(
            "ko", List.of("외국인등록은 어떻게 하나요?", "체류기간 연장은 언제 신청하나요?", "쓰레기는 어떻게 버리나요?"),
            "en", List.of("How do I register as a foreign resident?", "When should I apply to extend my stay?", "How do I throw away trash?"),
            "zh", List.of("怎么办理外国人登录？", "什么时候申请延长滞留期间？", "垃圾怎么扔？"),
            "vi", List.of("Đăng ký người nước ngoài như thế nào?", "Khi nào nên xin gia hạn thời gian lưu trú?", "Vứt rác như thế nào?"));

    private ChatSuggestions() {
    }

    static List<String> forUser(UserType userType, String language) {
        Map<String, List<String>> set = userType == null ? GENERAL : switch (userType) {
            case NEW -> NEW;
            case LIVING -> LIVING;
        };
        return set.getOrDefault(language, set.get("ko"));
    }
}
