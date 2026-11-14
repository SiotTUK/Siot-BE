package com.siot.backend.rag.ai;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;

/** Gemini 임베딩 (RETRIEVAL_QUERY). */
public class GeminiQueryEmbedder implements QueryEmbedder {

    private final EmbeddingModel model;
    private final int dimension;

    public GeminiQueryEmbedder(EmbeddingModel model, int dimension) {
        this.model = model;
        this.dimension = dimension;
    }

    @Override
    public float[] embedQuery(String question) {
        Embedding embedding;
        try {
            embedding = model.embed(question).content();
        } catch (RuntimeException e) {
            throw new AiUnavailableException("Gemini embedding failed", e);
        }
        float[] vector = embedding.vector();
        if (vector.length != dimension) {
            throw new AiUnavailableException(
                    "Unexpected embedding dimension " + vector.length + " (expected " + dimension + ")");
        }
        return vector;
    }
}
