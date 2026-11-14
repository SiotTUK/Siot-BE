package com.siot.backend.rag.ai;

import java.util.List;

/**
 * CB-07: 앞 대화를 가리키는 질문("그거 온라인으로도 돼요?")을 검색용 독립 질문으로 바꾼다.
 * 검색(임베딩)에만 쓰고, 답변 생성에는 원래 질문과 앞 대화를 그대로 넘긴다.
 */
@FunctionalInterface
public interface QueryRewriter {

    /**
     * @param history 오래된 것부터 최근 순 (비어 있지 않음)
     * @return 독립 질문 (질문과 같은 언어). 이미 독립적이면 그대로
     * @throws AiUnavailableException 호출 실패
     */
    String rewrite(String question, List<ChatTurn> history);
}
