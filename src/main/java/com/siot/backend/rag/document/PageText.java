package com.siot.backend.rag.document;

import java.util.Map;

/**
 * PDF 한 쪽의 텍스트.
 *
 * @param page   PDF 뷰어 기준 쪽 번호 (1부터)
 * @param styles 줄 글자(앞뒤 공백 제거) → 글자 모양. 모르면 빈 맵
 */
public record PageText(int page, String text, Map<String, LineStyle> styles) {

    public PageText(int page, String text) {
        this(page, text, Map.of());
    }

    /**
     * 줄의 글자 모양 (제목 판단용).
     *
     * @param fullWidth 쪽의 오른쪽 끝까지 찬 줄 (문장이 다음 줄로 이어짐)
     */
    public record LineStyle(float fontSize, boolean bold, boolean fullWidth) {
    }

    /** 같은 쪽의 줄 모양을 유지한 채 글자만 바꾼다 */
    public PageText withText(String newText) {
        return new PageText(page, newText, styles);
    }
}
