package com.siot.backend.rag.config;

import com.siot.backend.rag.ai.AnswerGenerator;
import com.siot.backend.rag.ai.GeminiAnswerGenerator;
import com.siot.backend.rag.ai.GeminiQueryEmbedder;
import com.siot.backend.rag.ai.QueryEmbedder;
import com.siot.backend.rag.ai.AiUnavailableException;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 챗봇 AI 연결 (CB-13: LangChain4j + Gemini).
 * API 키가 없으면 호출 시 {@link AiUnavailableException} 을 던지는 구현을 등록한다.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties({GeminiProperties.class, ChatbotProperties.class})
public class RagConfig {

    @Bean
    public QueryEmbedder queryEmbedder(GeminiProperties props) {
        if (!props.configured()) {
            log.warn("GEMINI_API_KEY is not set. Chatbot will answer with the 'unavailable' message.");
            return question -> {
                throw new AiUnavailableException("Gemini API key is not configured");
            };
        }
        GoogleAiEmbeddingModel model = GoogleAiEmbeddingModel.builder()
                .apiKey(props.apiKey())
                .modelName(props.embeddingModel())
                .outputDimensionality(props.embeddingDimension())
                .taskType(GoogleAiEmbeddingModel.TaskType.RETRIEVAL_QUERY)
                .timeout(props.timeout())
                .maxRetries(1)
                .build();
        return new GeminiQueryEmbedder(model, props.embeddingDimension());
    }

    @Bean
    public AnswerGenerator answerGenerator(GeminiProperties props) {
        if (!props.configured()) {
            return (question, language, context) -> {
                throw new AiUnavailableException("Gemini API key is not configured");
            };
        }
        GoogleAiGeminiChatModel model = GoogleAiGeminiChatModel.builder()
                .apiKey(props.apiKey())
                .modelName(props.chatModel())
                .temperature(props.temperature())
                .timeout(props.timeout())
                .maxRetries(1)
                .build();
        return new GeminiAnswerGenerator(model);
    }
}
