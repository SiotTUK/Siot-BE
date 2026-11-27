package com.siot.backend.rag.config;

import com.siot.backend.rag.ai.AnswerExplainer;
import com.siot.backend.rag.ai.AnswerGenerator;
import com.siot.backend.rag.ai.GeminiAnswerExplainer;
import com.siot.backend.rag.ai.GeminiAnswerGenerator;
import com.siot.backend.rag.ai.GeminiQueryEmbedder;
import com.siot.backend.rag.ai.GeminiQueryRewriter;
import com.siot.backend.rag.ai.QueryRewriter;
import com.siot.backend.rag.ai.QueryEmbedder;
import com.siot.backend.rag.ai.AiUnavailableException;
import dev.langchain4j.model.google.genai.GoogleGenAiChatModel;
import dev.langchain4j.model.google.genai.GoogleGenAiEmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 챗봇 AI 연결 (CB-13: LangChain4j + Gemini).
 * Gemini 설정(Vertex 프로젝트 또는 API 키)이 없으면 호출 시 {@link AiUnavailableException} 을 던지는 구현을 등록한다.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties({GeminiProperties.class, ChatbotProperties.class})
public class RagConfig {

    @Bean
    public QueryEmbedder queryEmbedder(GeminiProperties props) {
        if (!props.configured()) {
            log.warn("Neither GEMINI_PROJECT_ID nor GEMINI_API_KEY is set. Chatbot will answer with the 'unavailable' message.");
            return question -> {
                throw new AiUnavailableException("Gemini API key is not configured");
            };
        }
        GoogleGenAiEmbeddingModel model = GeminiModels.embedding(props)
                .modelName(props.embeddingModel())
                .outputDimensionality(props.embeddingDimension())
                .taskType(GoogleGenAiEmbeddingModel.TaskTypeEnum.RETRIEVAL_QUERY)
                .timeout(props.timeout())
                .maxRetries(1)
                .build();
        return new GeminiQueryEmbedder(model, props.embeddingDimension());
    }

    @Bean
    public AnswerGenerator answerGenerator(GeminiProperties props) {
        if (!props.configured()) {
            return (question, language, context, history) -> {
                throw new AiUnavailableException("Gemini API key is not configured");
            };
        }
        return new GeminiAnswerGenerator(chatModel(props, props.temperature()));
    }

    /** 앞 답변의 근거 설명 ("왜 그렇게 생각했어?"). 실패하면 저장된 출처 목록으로 답한다 */
    @Bean
    public AnswerExplainer answerExplainer(GeminiProperties props) {
        if (!props.configured()) {
            return (question, language, pastQuestion, pastAnswer, sources) -> {
                throw new AiUnavailableException("Gemini API key is not configured");
            };
        }
        return new GeminiAnswerExplainer(chatModel(props, 0.0));
    }

    /** CB-07: 이어지는 질문을 검색용 독립 질문으로 */
    @Bean
    public QueryRewriter queryRewriter(GeminiProperties props) {
        if (!props.configured()) {
            return (question, history) -> {
                throw new AiUnavailableException("Gemini API key is not configured");
            };
        }
        return new GeminiQueryRewriter(chatModel(props, 0.0));
    }

    private static GoogleGenAiChatModel chatModel(GeminiProperties props, double temperature) {
        return GeminiModels.chat(props)
                .modelName(props.chatModel())
                .temperature(temperature)
                .timeout(props.timeout())
                .maxRetries(1)
                .build();
    }
}
