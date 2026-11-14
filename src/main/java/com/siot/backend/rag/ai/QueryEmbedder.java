package com.siot.backend.rag.ai;

/** 질문을 검색용 벡터로 바꾼다. 문서 조각과 같은 모델·차원을 써야 한다. */
@FunctionalInterface
public interface QueryEmbedder {

    /** @throws AiUnavailableException 호출 실패 */
    float[] embedQuery(String question);
}
