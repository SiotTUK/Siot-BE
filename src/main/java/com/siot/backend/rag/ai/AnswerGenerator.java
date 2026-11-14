package com.siot.backend.rag.ai;

import java.util.List;

/** 검색된 문서 조각만 근거로 답변을 만든다 (CB-13). */
@FunctionalInterface
public interface AnswerGenerator {

    /** 근거 조각에 답이 없을 때 모델이 돌려주는 표시 */
    String NO_ANSWER = "NO_ANSWER";

    /**
     * @param language 질문 언어를 판별하지 못할 때 쓸 화면 언어 (ko/en/zh/vi)
     * @param history  앞선 대화 (오래된 것부터, 비어 있을 수 있음). 질문이 무엇을 가리키는지 이해하는 데만 쓴다 (CB-07)
     * @return 답변 문장, 또는 근거에 답이 없으면 {@link #NO_ANSWER}
     * @throws AiUnavailableException 호출 실패
     */
    String generate(String question, String language, List<RetrievedChunk> context, List<ChatTurn> history);
}
