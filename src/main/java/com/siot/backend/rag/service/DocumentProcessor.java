package com.siot.backend.rag.service;

import com.siot.backend.rag.ai.AiUnavailableException;
import com.siot.backend.rag.config.GeminiProperties;
import com.siot.backend.rag.document.BatchEmbedder;
import com.siot.backend.rag.document.DocumentChunker;
import com.siot.backend.rag.document.DocumentChunker.Chunk;
import com.siot.backend.rag.document.FileStorage;
import com.siot.backend.rag.document.PageRanges;
import com.siot.backend.rag.document.ExtractedContent;
import com.siot.backend.rag.document.PdfContentExtractor;
import com.siot.backend.rag.domain.ProcessingStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ADM-04: 한 버전을 추출 → 분할 → 임베딩 → 반영한다. 어느 단계든 실패하면 FAILED 와 "[단계] 이유" 를 남기고
 * 이전 현재 버전은 그대로 둔다. Gemini 호출은 DB 트랜잭션 밖에서 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentProcessor {

    private final DocumentVersionStateService stateService;
    private final DocumentIndexWriter indexWriter;
    private final FileStorage fileStorage;
    private final PdfContentExtractor extractor;
    private final DocumentChunker chunker;
    private final BatchEmbedder embedder;
    private final GeminiProperties geminiProperties;

    /** 단계 실패 */
    private static final class StageFailure extends RuntimeException {
        final ProcessingStage stage;

        StageFailure(ProcessingStage stage, String reason, Throwable cause) {
            super(reason, cause);
            this.stage = stage;
        }
    }

    public void process(Long versionId) {
        DocumentVersionStateService.Job job = stateService.start(versionId);
        if (job == null) {
            return;
        }
        long startedAt = System.currentTimeMillis();
        try {
            ExtractedContent content = extract(job);
            List<Chunk> chunks = split(content, job.documentName());
            List<float[]> vectors = embed(chunks, job.documentName());
            index(versionId, chunks, vectors);
            log.info("Document version {} indexed: {} pages ({}, coverage {}%), {} chunks in {} ms",
                    versionId, content.pageCount(), content.source(), Math.round(content.coverage() * 100), chunks.size(),
                    System.currentTimeMillis() - startedAt);
        } catch (StageFailure e) {
            log.warn("Document version {} failed at {}: {}", versionId, e.stage, e.getMessage(), e.getCause());
            stateService.fail(versionId, e.stage, e.getMessage());
        } catch (RuntimeException e) {
            log.error("Document version {} failed unexpectedly", versionId, e);
            stateService.fail(versionId, ProcessingStage.INDEX, "예상하지 못한 오류: " + e.getMessage());
        }
    }

    private ExtractedContent extract(DocumentVersionStateService.Job job) {
        ExtractedContent content;
        try {
            content = extractor.extract(fileStorage.load(job.fileStorageKey()),
                    PageRanges.parse(job.excludedPages()));
        } catch (Exception e) {
            throw new StageFailure(ProcessingStage.EXTRACT, "PDF를 읽을 수 없습니다: " + e.getMessage(), e);
        }
        if (content.isEmpty()) {
            throw new StageFailure(ProcessingStage.EXTRACT,
                    "PDF에서 텍스트를 찾지 못했습니다. 스캔한 이미지 PDF는 텍스트가 있는 PDF로 다시 올려 주세요.", null);
        }
        return content;
    }

    private List<Chunk> split(ExtractedContent content, String documentName) {
        try {
            List<Chunk> chunks = chunker.split(content, documentName);
            if (chunks.isEmpty()) {
                throw new StageFailure(ProcessingStage.SPLIT, "나눌 내용이 없습니다.", null);
            }
            return chunks;
        } catch (StageFailure e) {
            throw e;
        } catch (RuntimeException e) {
            throw new StageFailure(ProcessingStage.SPLIT, "조각을 나누지 못했습니다: " + e.getMessage(), e);
        }
    }

    private List<float[]> embed(List<Chunk> chunks, String documentName) {
        try {
            return embedder.embed(chunks.stream().map(Chunk::content).toList(), documentName);
        } catch (AiUnavailableException e) {
            throw new StageFailure(ProcessingStage.EMBEDDING, "Gemini 임베딩 실패: " + rootMessage(e), e);
        }
    }

    private void index(Long versionId, List<Chunk> chunks, List<float[]> vectors) {
        try {
            indexWriter.write(versionId, chunks, vectors, geminiProperties.embeddingModel());
        } catch (RuntimeException e) {
            throw new StageFailure(ProcessingStage.INDEX, "검색 반영 실패: " + rootMessage(e), e);
        }
    }

    /** 가장 안쪽 원인의 메시지 (Gemini 오류 본문 등). 줄바꿈은 한 줄로 */
    private static String rootMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
        return message.replaceAll("\\s+", " ").strip();
    }
}
