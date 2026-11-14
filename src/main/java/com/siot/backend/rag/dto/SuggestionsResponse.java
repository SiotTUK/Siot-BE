package com.siot.backend.rag.dto;

import java.util.List;

/**
 * CB-08: 추천 질문 (버튼).
 *
 * @param userType 기준이 된 사용자 유형 (NEW / LIVING, 온보딩 전이면 null)
 */
public record SuggestionsResponse(String userType, String language, List<String> questions) {
}
