package com.siot.backend.rag.ai;

/**
 * 앞선 대화 한 턴 (CB-07). 개인정보는 이미 가려진 값이다.
 *
 * @param answer 근거로 답한 경우(ANSWERED)만 답변, 그 외(근거 없음·오류)는 null
 */
public record ChatTurn(String question, String answer) {
}
