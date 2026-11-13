package com.siot.backend.rag.config;

import com.siot.backend.rag.ai.AiUnavailableException;
import com.siot.backend.rag.ai.DocumentEmbedder;
import com.siot.backend.rag.ai.GeminiDocumentEmbedder;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 문서 적재 AI 연결 (ADM-02~04). {@link RagConfig} 와 같은 방식이다.
 * API 키가 없으면 호출 시 {@link AiUnavailableException} 을 던지는 구현을 등록한다 (적재는 FAILED 가 된다).
 */
@Configuration
public class DocumentAiConfig {

    /** 적재는 한 번에 여러 묶음을 보내므로 챗봇보다 재시도를 넉넉히 둔다 (429 대비) */
    private static final int MAX_RETRIES = 3;

    @Bean
    public DocumentEmbedder documentEmbedder(GeminiProperties props) {
        if (!props.configured()) {
            return (texts, title) -> {
                throw new AiUnavailableException("Gemini API key is not configured");
            };
        }
        GoogleAiEmbeddingModel model = GoogleAiEmbeddingModel.builder()
                .apiKey(props.apiKey())
                .modelName(props.embeddingModel())
                .outputDimensionality(props.embeddingDimension())
                .taskType(GoogleAiEmbeddingModel.TaskType.RETRIEVAL_DOCUMENT)
                .titleMetadataKey(GeminiDocumentEmbedder.TITLE_METADATA_KEY)
                .timeout(props.timeout())
                .maxRetries(MAX_RETRIES)
                .build();
        return new GeminiDocumentEmbedder(model, props.embeddingDimension());
    }
}
