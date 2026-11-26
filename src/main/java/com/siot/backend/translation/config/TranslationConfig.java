package com.siot.backend.translation.config;

import com.siot.backend.rag.ai.AiUnavailableException;
import com.siot.backend.rag.config.GeminiProperties;
import com.siot.backend.translation.BillReader;
import com.siot.backend.translation.GeminiBillReader;
import com.siot.backend.translation.GeminiTranslator;
import com.siot.backend.translation.Translator;
import com.siot.backend.rag.config.GeminiModels;
import dev.langchain4j.model.google.genai.GoogleGenAiChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

/**
 * 번역 AI 연결 (COM-02, EVE-02). {@link com.siot.backend.rag.config.RagConfig} 와 같은 방식이다.
 * Gemini 설정이 없으면 호출 시 {@link AiUnavailableException} 을 던지는 구현을 등록한다.
 */
@Configuration
@EnableConfigurationProperties(TranslationProperties.class)
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
        GoogleGenAiChatModel model = GeminiModels.chat(props)
                .modelName(props.chatModel())
                .temperature(0.0)
                .timeout(props.timeout())
                .maxRetries(MAX_RETRIES)
                .build();
        return new GeminiTranslator(model, jsonMapper);
    }

    /**
     * RUL-07 고지서 사진 읽기. 모델·제한 시간은 siot.translation.bill-model·bill-timeout (모델이 비어 있으면 채팅 모델).
     * 429·503 재시도는 GeminiBillReader 가 한 번만 한다 (모델 자체 재시도 없음)
     */
    @Bean
    public BillReader billReader(GeminiProperties props, TranslationProperties translationProps,
                                 JsonMapper jsonMapper) {
        if (!props.configured()) {
            return (image, mimeType, language) -> {
                throw new AiUnavailableException("Gemini API key is not configured");
            };
        }
        GoogleGenAiChatModel model = GeminiModels.chat(props)
                .modelName(translationProps.billModelOr(props.chatModel()))
                .temperature(0.0)
                .timeout(translationProps.billTimeout())
                .maxRetries(0)
                .logRequests(false)
                .logResponses(false)
                .build();
        return new GeminiBillReader(model, jsonMapper, translationProps.billRetryWait());
    }
}
