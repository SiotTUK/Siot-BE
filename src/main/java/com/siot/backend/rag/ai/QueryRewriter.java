package com.siot.backend.rag.ai;

import java.util.List;

/**
 * 질문을 검색용 한국어 독립 질문으로 바꾼다. 검색(임베딩)에만 쓰고, 답변 생성에는 원래 질문과 앞 대화를 그대로 넘긴다.
 * <ul>
 *   <li>CB-07: 앞 대화를 가리키는 질문("그거 온라인으로도 돼요?")을 독립 질문으로</li>
 *   <li>CB-14: 외국어 질문은 한국어로 (자료가 한국어라 외국어 질문으로는 찾지 못하는 조각이 있음)</li>
 * </ul>
 */
@FunctionalInterface
public interface QueryRewriter {

    /**
     * @param history 오래된 것부터 최근 순 (외국어 첫 질문이면 비어 있음)
     * @return 한국어 독립 질문. 이미 한국어 독립 질문이면 그대로
     * @throws AiUnavailableException 호출 실패
     */
    String rewrite(String question, List<ChatTurn> history);
}
