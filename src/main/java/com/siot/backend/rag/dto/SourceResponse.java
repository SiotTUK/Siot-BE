package com.siot.backend.rag.dto;

/**
 * CB-17: 답변 근거. 화면에는 documentName 만 보이고, 누르면 쪽수와 원문 링크를 펼친다.
 * 문서가 삭제된 경우 documentName 등은 null.
 */
public record SourceResponse(
        int order,
        String documentName,
        Integer pageStart,
        Integer pageEnd,
        String sourceUrl,
        Double relevanceScore
) {
}
