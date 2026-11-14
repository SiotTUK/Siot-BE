package com.siot.backend.rag.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * @param role              USER / ASSISTANT
 * @param answerStatus      답변만: ANSWERED / NO_EVIDENCE / ERROR
 * @param sources           답변 근거 (CB-17, ANSWERED 일 때만)
 * @param relatedPages      관련 서비스 화면 (CB-10, '관련 내용 보기')
 * @param emergencyContacts 응급 질문이면 답변 맨 위에 보여 줄 119/112 (CB-09). 아니면 빈 목록
 * @param referral          비자 발급 가능 여부·법률·의료 판단 질문이면 고정 안내 + 담당 기관 (CB-11). 아니면 null
 * @param helpful           답변 평가 (CB-04). 평가 전이면 null
 */
public record MessageResponse(
        Long messageId,
        String role,
        String content,
        String answerStatus,
        List<SourceResponse> sources,
        List<RelatedPage> relatedPages,
        List<EmergencyContact> emergencyContacts,
        Referral referral,
        Boolean helpful,
        OffsetDateTime createdAt
) {
}
