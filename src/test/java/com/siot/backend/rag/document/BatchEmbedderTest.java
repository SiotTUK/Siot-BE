package com.siot.backend.rag.document;

import com.siot.backend.rag.ai.AiUnavailableException;
import com.siot.backend.rag.ai.DocumentEmbedder;
import com.siot.backend.rag.config.DocumentProperties;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.RateLimitException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BatchEmbedderTest {

    private static final Duration PAUSE = Duration.ofSeconds(2);
    private static final Duration RETRY_WAIT = Duration.ofSeconds(60);

    private final DocumentEmbedder embedder = mock(DocumentEmbedder.class);
    private final List<Duration> sleeps = new ArrayList<>();
    private final BatchEmbedder batchEmbedder = new BatchEmbedder(embedder,
            new DocumentProperties("unused", 2, PAUSE, RETRY_WAIT, 2), sleeps::add);

    @Test
    void embedsInBatchesWithPauseBetween() {
        when(embedder.embedAll(anyList(), eq("문서"))).thenAnswer(call -> vectors(((List<?>) call.getArgument(0)).size()));

        List<float[]> result = batchEmbedder.embed(texts(5), "문서");

        assertThat(result).hasSize(5);
        verify(embedder).embedAll(List.of("t0", "t1"), "문서");
        verify(embedder).embedAll(List.of("t2", "t3"), "문서");
        verify(embedder).embedAll(List.of("t4"), "문서");
        assertThat(sleeps).containsExactly(PAUSE, PAUSE);
    }

    @Test
    void waitsAndRetriesOnRateLimit() {
        when(embedder.embedAll(anyList(), eq("문서")))
                .thenThrow(unavailable(new RateLimitException("429 RESOURCE_EXHAUSTED")))
                .thenReturn(vectors(1));

        assertThat(batchEmbedder.embed(texts(1), "문서")).hasSize(1);
        assertThat(sleeps).containsExactly(RETRY_WAIT);
    }

    @Test
    void waitsAndRetriesOnServiceUnavailable() {
        AiUnavailableException busy = unavailable(new InternalServerException(
                "high demand", new HttpException(503, "{\"error\":{\"status\":\"UNAVAILABLE\"}}")));
        when(embedder.embedAll(anyList(), eq("문서"))).thenThrow(busy).thenThrow(busy).thenReturn(vectors(1));

        assertThat(batchEmbedder.embed(texts(1), "문서")).hasSize(1);
        assertThat(sleeps).containsExactly(RETRY_WAIT, RETRY_WAIT);
    }

    @Test
    void failsAfterMaxRetries() {
        when(embedder.embedAll(anyList(), eq("문서"))).thenThrow(unavailable(new RateLimitException("429")));

        assertThatThrownBy(() -> batchEmbedder.embed(texts(1), "문서")).isInstanceOf(AiUnavailableException.class);
        verify(embedder, times(3)).embedAll(anyList(), eq("문서"));
    }

    @Test
    void doesNotRetryOtherErrors() {
        when(embedder.embedAll(anyList(), eq("문서"))).thenThrow(unavailable(new InternalServerException(
                "internal", new HttpException(500, "{}"))));

        assertThatThrownBy(() -> batchEmbedder.embed(texts(1), "문서")).isInstanceOf(AiUnavailableException.class);
        verify(embedder, times(1)).embedAll(anyList(), eq("문서"));
        assertThat(sleeps).isEmpty();
    }

    private static AiUnavailableException unavailable(Throwable cause) {
        return new AiUnavailableException("Gemini document embedding failed", cause);
    }

    private static List<String> texts(int count) {
        return IntStream.range(0, count).mapToObj(i -> "t" + i).toList();
    }

    private static List<float[]> vectors(int count) {
        return IntStream.range(0, count).mapToObj(i -> new float[]{1}).toList();
    }
}
