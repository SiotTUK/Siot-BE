package com.siot.backend.rag.service;

import com.siot.backend.rag.document.DocumentChunker.Chunk;
import com.siot.backend.rag.domain.DocumentChunk;
import com.siot.backend.rag.domain.DocumentVersion;
import com.siot.backend.rag.repository.DocumentChunkRepository;
import com.siot.backend.rag.repository.DocumentRepository;
import com.siot.backend.rag.repository.DocumentVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * ADM-04 [INDEX]: 조각 저장과 버전 전환을 한 트랜잭션으로 한다.
 * 이전 현재 버전을 먼저 내리고(flush) 새 버전을 현재 버전으로 올린다 (문서당 현재 버전 1개 unique 인덱스).
 * 중간에 실패하면 저장한 조각까지 모두 되돌아가고 이전 버전이 그대로 검색된다.
 */
@Service
@RequiredArgsConstructor
public class DocumentIndexWriter {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final DocumentChunkRepository chunkRepository;
    private final Clock clock;

    @Transactional
    public void write(Long versionId, List<Chunk> chunks, List<float[]> vectors, String embeddingModel) {
        if (chunks.size() != vectors.size()) {
            throw new IllegalArgumentException("chunks " + chunks.size() + " != vectors " + vectors.size());
        }
        DocumentVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalStateException("Document version " + versionId + " was deleted"));
        Long documentId = version.getDocument().getId();
        // 업로드·재실행과 겹치지 않도록 문서 행을 잠근다
        documentRepository.findByIdForUpdate(documentId);

        List<DocumentChunk> entities = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            Chunk chunk = chunks.get(i);
            entities.add(DocumentChunk.create(version, chunk.chunkNo(), chunk.pageStart(), chunk.pageEnd(),
                    chunk.content(), embeddingModel, vectors.get(i)));
        }
        chunkRepository.saveAll(entities);

        versionRepository.findByDocumentIdAndCurrentTrue(documentId)
                .filter(current -> !current.getId().equals(versionId))
                .ifPresent(current -> {
                    current.release();
                    versionRepository.flush();
                });
        version.complete(OffsetDateTime.now(clock));
    }
}
