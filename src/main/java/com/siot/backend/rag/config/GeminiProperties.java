package com.siot.backend.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Gemini API 설정. apiKey 가 비어 있으면 챗봇은 "지금은 답변할 수 없어요" 로 응답한다 (앱 기동은 정상).
 *
 * @param embeddingModel     질문 임베딩 모델. 문서 적재(역할 B)와 반드시 같아야 한다
 * @param embeddingDimension 임베딩 차원. document_chunk.embedding VECTOR(768) 과 같아야 한다
 */
@ConfigurationProperties(prefix = "siot.gemini")
public record GeminiProperties(
        String apiKey,
        String chatModel,
        String embeddingModel,
        int embeddingDimension,
        double temperature,
        Duration timeout
) {

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
