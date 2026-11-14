package com.siot.backend.rag.service;

import com.siot.backend.rag.ai.RetrievedChunk;
import com.siot.backend.rag.config.GeminiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * CB-14: 질문 벡터와 가장 가까운 문서 조각을 찾는다 (pgvector 코사인 거리, HNSW 인덱스).
 * <p>
 * 검색 대상: 사용 중인 문서(document.is_active)의 현재 버전(document_version.is_current) 중
 * 반영이 끝난(processing_status = 'COMPLETED') 버전의 조각이며, 같은 임베딩 모델로 만든 것.
 * 문서 적재 쪽 약속(COMPLETED 뒤에만 is_current)이 어긋나도 반영 중·실패 버전은 검색되지 않는다 (ADM-04).
 */
@Component
@RequiredArgsConstructor
public class ChunkRetriever {

    private static final String SQL = """
            SELECT c.chunk_id, c.content, c.page_start, c.page_end,
                   d.name AS document_name, d.source_url,
                   1 - (c.embedding <=> CAST(? AS vector)) AS relevance
            FROM document_chunk c
            JOIN document_version v ON v.document_version_id = c.document_version_id
            JOIN document d ON d.document_id = v.document_id
            WHERE v.is_current
              AND v.processing_status = 'COMPLETED'
              AND d.is_active
              AND c.embedding IS NOT NULL
              AND c.embedding_model = ?
            ORDER BY c.embedding <=> CAST(? AS vector)
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final GeminiProperties geminiProperties;

    public List<RetrievedChunk> search(float[] queryVector, int limit) {
        String vector = toVectorLiteral(queryVector);
        return jdbcTemplate.query(SQL, (rs, rowNum) -> new RetrievedChunk(
                        rs.getLong("chunk_id"),
                        rs.getString("content"),
                        (Integer) rs.getObject("page_start"),
                        (Integer) rs.getObject("page_end"),
                        rs.getString("document_name"),
                        rs.getString("source_url"),
                        rs.getDouble("relevance")),
                vector, geminiProperties.embeddingModel(), vector, limit);
    }

    static String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 10).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }
}
