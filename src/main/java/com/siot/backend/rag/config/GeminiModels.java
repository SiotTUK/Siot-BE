package com.siot.backend.rag.config;

import dev.langchain4j.model.google.genai.GoogleGenAiChatModel;
import dev.langchain4j.model.google.genai.GoogleGenAiEmbeddingModel;

/**
 * Gemini 모델 빌더에 연결 방식(Vertex AI 또는 AI Studio API 키)만 채워 돌려준다.
 * 모델 이름·온도·재시도 등은 호출하는 쪽에서 정한다.
 */
public final class GeminiModels {

    private GeminiModels() {
    }

    public static GoogleGenAiChatModel.Builder chat(GeminiProperties props) {
        GoogleGenAiChatModel.Builder builder = GoogleGenAiChatModel.builder();
        if (props.vertex()) {
            return builder.projectId(props.projectId()).location(props.location());
        }
        return builder.apiKey(props.apiKey());
    }

    public static GoogleGenAiEmbeddingModel.Builder embedding(GeminiProperties props) {
        GoogleGenAiEmbeddingModel.Builder builder = GoogleGenAiEmbeddingModel.builder();
        if (props.vertex()) {
            return builder.projectId(props.projectId()).location(props.location());
        }
        return builder.apiKey(props.apiKey());
    }
}
