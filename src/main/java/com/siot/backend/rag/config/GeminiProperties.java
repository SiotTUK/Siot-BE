package com.siot.backend.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Gemini 설정. 둘 중 하나가 있으면 호출한다 (둘 다 없으면 챗봇은 "지금은 답변할 수 없어요" 로 응답하고 앱 기동은 정상).
 * <ul>
 *     <li>Vertex AI: projectId 가 있으면 우선. 인증은 ADC (GCE VM 서비스 계정, 로컬은 gcloud auth application-default login)</li>
 *     <li>AI Studio: apiKey</li>
 * </ul>
 *
 * @param projectId          Vertex AI 프로젝트 ID (GEMINI_PROJECT_ID). 비어 있으면 apiKey 사용
 * @param location           Vertex AI 리전 (GEMINI_LOCATION). gemini-3.x 는 global 에서만 제공된다
 * @param embeddingModel     질문 임베딩 모델. 문서 적재(역할 B)와 반드시 같아야 한다
 * @param embeddingDimension 임베딩 차원. document_chunk.embedding VECTOR(768) 과 같아야 한다
 */
@ConfigurationProperties(prefix = "siot.gemini")
public record GeminiProperties(
        String apiKey,
        String projectId,
        String location,
        String chatModel,
        String embeddingModel,
        int embeddingDimension,
        double temperature,
        Duration timeout
) {

    public boolean vertex() {
        return projectId != null && !projectId.isBlank();
    }

    public boolean configured() {
        return vertex() || (apiKey != null && !apiKey.isBlank());
    }
}
