package com.siot.backend.rag.dto;

import io.swagger.v3.oas.annotations.media.Schema;

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
@Schema(description = "챗봇 메시지 (질문 또는 답변)")
public record MessageResponse(
        @Schema(description = "메시지 ID", example = "32") Long messageId,
        @Schema(description = "USER = 질문, ASSISTANT = 답변", allowableValues = {"USER", "ASSISTANT"}) String role,
        @Schema(description = "내용. 질문은 개인정보를 가린 값, NO_EVIDENCE·ERROR 답변은 문의처 안내 문구") String content,
        @Schema(description = "답변만. ANSWERED = 자료 근거로 답변, NO_EVIDENCE = 자료 없음(CB-12), ERROR = AI 오류(CB-02). 질문은 null",
                allowableValues = {"ANSWERED", "NO_EVIDENCE", "ERROR"}, nullable = true) String answerStatus,
        @Schema(description = "답변 근거 (CB-17, ANSWERED 일 때만)") List<SourceResponse> sources,
        @Schema(description = "관련 서비스 화면 (CB-10 '관련 내용 보기', 최대 3개)") List<RelatedPage> relatedPages,
        @Schema(description = "응급 질문이면 답변 맨 위에 보여 줄 119·112 (CB-09). 아니면 빈 목록") List<EmergencyContact> emergencyContacts,
        @Schema(description = "비자 발급 가능 여부·법률·의료 판단 질문이면 고정 안내 + 담당 기관 (CB-11). 아니면 null", nullable = true) Referral referral,
        @Schema(description = "답변 평가 (CB-04). 평가 전이면 null", nullable = true) Boolean helpful,
        @Schema(description = "작성 시각") OffsetDateTime createdAt
) {
}
