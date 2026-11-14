package com.siot.backend.rag.dto;

import java.util.List;
import java.util.Map;

/** CB-09: 답변 맨 위에 보여 줄 긴급 연락처 */
public record EmergencyContact(String number, String label) {

    private static final Map<String, List<EmergencyContact>> BY_LANGUAGE = Map.of(
            "ko", List.of(new EmergencyContact("119", "화재·구급"), new EmergencyContact("112", "경찰")),
            "en", List.of(new EmergencyContact("119", "Fire & Ambulance"), new EmergencyContact("112", "Police")),
            "zh", List.of(new EmergencyContact("119", "火警·急救"), new EmergencyContact("112", "报警")),
            "vi", List.of(new EmergencyContact("119", "Cứu hỏa & Cấp cứu"), new EmergencyContact("112", "Cảnh sát")));

    public static List<EmergencyContact> forLanguage(String language) {
        return BY_LANGUAGE.getOrDefault(language, BY_LANGUAGE.get("ko"));
    }
}
