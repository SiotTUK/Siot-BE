package com.siot.backend.rag.ai;

/**
 * 검색된 문서 조각.
 *
 * @param relevance 1 - 코사인 거리 (클수록 관련 높음)
 */
public record RetrievedChunk(
        Long chunkId,
        String content,
        Integer pageStart,
        Integer pageEnd,
        String documentName,
        String sourceUrl,
        double relevance
) {
}
