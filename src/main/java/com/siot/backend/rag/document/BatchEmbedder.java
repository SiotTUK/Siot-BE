package com.siot.backend.rag.document;

import com.siot.backend.rag.ai.AiUnavailableException;
import com.siot.backend.rag.ai.DocumentEmbedder;
import com.siot.backend.rag.config.DocumentProperties;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.RateLimitException;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * ADM-04 [EMBEDDING]: 무료 등급 한도 안에서 조각을 임베딩한다 (API 키를 챗봇과 같이 쓴다).
 * <ul>
 *     <li>embeddingBatchSize 개씩 묶어서 호출하고, 묶음 사이에 embeddingBatchPause 만큼 쉰다</li>
 *     <li>429(한도 초과)·503(사용량 폭주) 이면 embeddingRetryWait 만큼 기다렸다 같은 묶음을 다시 보낸다</li>
 *     <li>embeddingMaxRetries 번 넘게 실패하거나 다른 오류면 {@link AiUnavailableException}</li>
 * </ul>
 */
@Slf4j
public class BatchEmbedder {

    /** 테스트에서 실제로 기다리지 않도록 대기를 분리한다 */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final DocumentEmbedder embedder;
    private final DocumentProperties properties;
    private final Sleeper sleeper;

    public BatchEmbedder(DocumentEmbedder embedder, DocumentProperties properties, Sleeper sleeper) {
        this.embedder = embedder;
        this.properties = properties;
        this.sleeper = sleeper;
    }

    public List<float[]> embed(List<String> texts, String title) {
        List<float[]> vectors = new ArrayList<>(texts.size());
        int batchSize = properties.embeddingBatchSize();
        for (int from = 0; from < texts.size(); from += batchSize) {
            if (from > 0) {
                sleep(properties.embeddingBatchPause());
            }
            List<String> batch = texts.subList(from, Math.min(from + batchSize, texts.size()));
            vectors.addAll(embedWithRetry(batch, title));
        }
        return vectors;
    }

    private List<float[]> embedWithRetry(List<String> batch, String title) {
        for (int attempt = 0; ; attempt++) {
            try {
                return embedder.embedAll(batch, title);
            } catch (AiUnavailableException e) {
                if (!isTransient(e) || attempt >= properties.embeddingMaxRetries()) {
                    throw e;
                }
                log.warn("Gemini embedding busy ({}), retrying in {} ({}/{})",
                        statusOf(e), properties.embeddingRetryWait(), attempt + 1, properties.embeddingMaxRetries());
                sleep(properties.embeddingRetryWait());
            }
        }
    }

    /** 429 RESOURCE_EXHAUSTED, 503 UNAVAILABLE */
    static boolean isTransient(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof RateLimitException) {
                return true;
            }
            if (cause instanceof InternalServerException && cause.getCause() instanceof HttpException http) {
                return http.statusCode() == 503;
            }
            if (cause instanceof HttpException http) {
                return http.statusCode() == 429 || http.statusCode() == 503;
            }
        }
        return false;
    }

    private static String statusOf(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpException http) {
                return String.valueOf(http.statusCode());
            }
        }
        return error.getCause() == null ? "unknown" : error.getCause().getClass().getSimpleName();
    }

    private void sleep(Duration duration) {
        if (duration.isZero() || duration.isNegative()) {
            return;
        }
        try {
            sleeper.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiUnavailableException("Interrupted while waiting for Gemini", e);
        }
    }
}
