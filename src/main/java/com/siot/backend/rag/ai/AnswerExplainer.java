package com.siot.backend.rag.ai;

import java.util.List;

/**
 * "왜 그렇게 생각했어?" 같은 질문에, 앞서 한 답변을 그 답변 당시 저장한 근거 조각으로 설명한다.
 * 새로 검색한 조각을 앞 답변의 근거처럼 쓰지 않는다.
 */
@FunctionalInterface
public interface AnswerExplainer {

    /**
     * @param question     지금 질문 ("왜 그렇게 답했어?")
     * @param language     답할 언어 (ko/en/zh/vi)
     * @param pastQuestion 설명할 답변의 질문
     * @param pastAnswer   설명할 답변
     * @param sources      그 답변을 만들 때 넘긴 근거 조각 (저장된 것, 비어 있지 않음)
     * @return 설명 문장
     * @throws AiUnavailableException 호출 실패
     */
    String explain(String question, String language, String pastQuestion, String pastAnswer,
                   List<RetrievedChunk> sources);
}
