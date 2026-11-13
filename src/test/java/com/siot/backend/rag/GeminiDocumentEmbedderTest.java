package com.siot.backend.rag;

import com.siot.backend.rag.ai.AiUnavailableException;
import com.siot.backend.rag.ai.GeminiDocumentEmbedder;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GeminiDocumentEmbedderTest {

    private static final int DIMENSION = 4;

    private final EmbeddingModel model = mock(EmbeddingModel.class);
    private final GeminiDocumentEmbedder embedder = new GeminiDocumentEmbedder(model, DIMENSION);

    @Test
    @SuppressWarnings("unchecked")
    void embedsInOneCallWithTitleAndNormalizes() {
        when(model.embedAll(anyList())).thenReturn(Response.from(List.of(
                Embedding.from(new float[]{3, 4, 0, 0}),
                Embedding.from(new float[]{0, 0, 0, 2}))));

        List<float[]> vectors = embedder.embedAll(List.of("외국인등록 안내", "체류기간 연장"), "외국인 생활 가이드");

        assertThat(vectors).hasSize(2);
        assertThat(vectors.get(0)).containsExactly(new float[]{0.6f, 0.8f, 0, 0}, within(1e-6f));
        assertThat(vectors.get(1)).containsExactly(new float[]{0, 0, 0, 1}, within(1e-6f));

        ArgumentCaptor<List<TextSegment>> segments = ArgumentCaptor.forClass(List.class);
        verify(model).embedAll(segments.capture());
        assertThat(segments.getValue()).extracting(TextSegment::text)
                .containsExactly("외국인등록 안내", "체류기간 연장");
        assertThat(segments.getValue()).allSatisfy(segment -> assertThat(
                segment.metadata().getString(GeminiDocumentEmbedder.TITLE_METADATA_KEY)).isEqualTo("외국인 생활 가이드"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendsNoTitleWhenBlank() {
        when(model.embedAll(anyList())).thenReturn(Response.from(List.of(Embedding.from(new float[]{1, 0, 0, 0}))));

        embedder.embedAll(List.of("내용"), " ");

        ArgumentCaptor<List<TextSegment>> segments = ArgumentCaptor.forClass(List.class);
        verify(model).embedAll(segments.capture());
        assertThat(segments.getValue().getFirst().metadata().getString(GeminiDocumentEmbedder.TITLE_METADATA_KEY)).isNull();
    }

    @Test
    void emptyInputSkipsCall() {
        assertThat(embedder.embedAll(List.of(), "제목")).isEmpty();
        verifyNoInteractions(model);
    }

    @Test
    void failsOnWrongDimension() {
        when(model.embedAll(anyList())).thenReturn(Response.from(List.of(Embedding.from(new float[]{1, 0, 0}))));

        assertThatThrownBy(() -> embedder.embedAll(List.of("내용"), null))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("dimension");
    }

    @Test
    void failsOnCountMismatch() {
        when(model.embedAll(anyList())).thenReturn(Response.from(List.of(Embedding.from(new float[]{1, 0, 0, 0}))));

        assertThatThrownBy(() -> embedder.embedAll(List.of("하나", "둘"), null))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("count");
    }

    @Test
    void failsOnZeroVector() {
        when(model.embedAll(anyList())).thenReturn(Response.from(List.of(Embedding.from(new float[]{0, 0, 0, 0}))));

        assertThatThrownBy(() -> embedder.embedAll(List.of("내용"), null))
                .isInstanceOf(AiUnavailableException.class);
    }

    @Test
    void wrapsModelError() {
        when(model.embedAll(anyList())).thenThrow(new RuntimeException("429 Too Many Requests"));

        assertThatThrownBy(() -> embedder.embedAll(List.of("내용"), null))
                .isInstanceOf(AiUnavailableException.class)
                .hasCauseInstanceOf(RuntimeException.class);
    }
}
