package com.siot.backend.rag.ai;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Gemini 임베딩 (RETRIEVAL_DOCUMENT). 모델이 100개씩 묶어 batchEmbedContents 로 호출한다.
 * gemini-embedding-001 은 768 차원에서 정규화되지 않은 벡터를 주므로 저장 전에 L2 정규화한다.
 */
public class GeminiDocumentEmbedder implements DocumentEmbedder {

    /** GoogleAiEmbeddingModel.titleMetadataKey 와 같아야 제목이 요청에 들어간다 */
    public static final String TITLE_METADATA_KEY = "title";

    private final EmbeddingModel model;
    private final int dimension;

    public GeminiDocumentEmbedder(EmbeddingModel model, int dimension) {
        this.model = model;
        this.dimension = dimension;
    }

    @Override
    public List<float[]> embedAll(List<String> texts, String title) {
        if (texts.isEmpty()) {
            return List.of();
        }
        Metadata metadata = title == null || title.isBlank()
                ? new Metadata()
                : Metadata.from(TITLE_METADATA_KEY, title);
        List<TextSegment> segments = texts.stream()
                .map(text -> TextSegment.from(text, metadata.copy()))
                .toList();

        List<Embedding> embeddings;
        try {
            embeddings = model.embedAll(segments).content();
        } catch (RuntimeException e) {
            throw new AiUnavailableException("Gemini document embedding failed", e);
        }
        if (embeddings == null || embeddings.size() != texts.size()) {
            throw new AiUnavailableException("Unexpected embedding count "
                    + (embeddings == null ? 0 : embeddings.size()) + " (expected " + texts.size() + ")");
        }

        List<float[]> vectors = new ArrayList<>(embeddings.size());
        for (Embedding embedding : embeddings) {
            vectors.add(normalize(embedding.vector()));
        }
        return vectors;
    }

    private float[] normalize(float[] vector) {
        if (vector.length != dimension) {
            throw new AiUnavailableException(
                    "Unexpected embedding dimension " + vector.length + " (expected " + dimension + ")");
        }
        double sum = 0;
        for (float v : vector) {
            sum += (double) v * v;
        }
        double norm = Math.sqrt(sum);
        if (norm == 0 || Double.isNaN(norm)) {
            throw new AiUnavailableException("Embedding vector has no length");
        }
        float[] normalized = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = (float) (vector[i] / norm);
        }
        return normalized;
    }
}
