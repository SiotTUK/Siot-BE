package com.siot.backend.translation.config;

import com.siot.backend.rag.ai.AiUnavailableException;
import com.siot.backend.rag.config.GeminiProperties;
import com.siot.backend.translation.GeminiTranslator;
import com.siot.backend.translation.Translator;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

/**
 * 번역 AI 연결 (COM-02, EVE-02). {@link com.siot.backend.rag.config.RagConfig} 와 같은 방식이다.
 * API 키가 없으면 호출 시 {@link AiUnavailableException} 을 던지는 구현을 등록한다.
 */
@Configuration
public class TranslationConfig {

    /** 수집처럼 여러 건을 이어서 번역하므로 챗봇보다 재시도를 넉넉히 둔다 (429 대비) */
    private static final int MAX_RETRIES = 3;

    @Bean
    public Translator translator(GeminiProperties props, JsonMapper jsonMapper) {
        if (!props.configured()) {
            return new Translator() {
                @Override
                public String translate(String text, String targetLanguage) {
                    throw new AiUnavailableException("Gemini API key is not configured");
                }

                @Override
                public Map<String, String> translateAll(String text) {
                    throw new AiUnavailableException("Gemini API key is not configured");
                }
            };
        }
        GoogleAiGeminiChatModel model = GoogleAiGeminiChatModel.builder()
                .apiKey(props.apiKey())
                .modelName(props.chatModel())
                .temperature(0.0)
                .timeout(props.timeout())
                .maxRetries(MAX_RETRIES)
                .build();
        return new GeminiTranslator(model, jsonMapper);
    }
}
