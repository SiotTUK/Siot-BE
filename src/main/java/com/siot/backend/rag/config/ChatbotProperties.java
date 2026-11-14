package com.siot.backend.rag.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 챗봇 검색·답변 설정.
 *
 * @param minRelevance 근거로 인정하는 최소 관련도 (1 - 코사인 거리). 가장 관련 높은 조각이 이보다 낮으면
 *                     답변을 만들지 않고 "자료에서 찾지 못했어요" 로 안내한다 (CB-12).
 *                     실제 문서가 적재되면 평가 세트(CB-01)로 다시 맞춘다.
 * @param topK         검색해서 답변 근거로 넘기는 조각 수
 * @param maxSources   화면에 표시하는 출처 수 (CB-17)
 * @param maxQuestionLength 질문 최대 길이
 */
@Validated
@ConfigurationProperties(prefix = "siot.chatbot")
public record ChatbotProperties(
        @DecimalMin("0.0") @DecimalMax("1.0") double minRelevance,
        @Min(1) int topK,
        @Min(1) int maxSources,
        @Min(1) int maxQuestionLength
) {
}
