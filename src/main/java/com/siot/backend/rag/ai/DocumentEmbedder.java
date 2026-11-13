package com.siot.backend.rag.ai;

import java.util.List;

/**
 * 문서 조각을 검색용 벡터로 바꾼다 (문서 적재, ADM-02~04).
 * 질문 임베딩({@link QueryEmbedder})과 같은 모델·차원을 써야 한다.
 */
@FunctionalInterface
public interface DocumentEmbedder {

    /**
     * @param texts 조각 내용. 묶어서 호출한다
     * @param title 문서 이름 (임베딩 품질용 제목). 없으면 null
     * @return texts 와 같은 순서의 벡터. L2 정규화되어 있다
     * @throws AiUnavailableException 호출 실패 또는 결과 이상
     */
    List<float[]> embedAll(List<String> texts, String title);
}
