package com.siot.backend.rag.document;

/**
 * PDF 한 쪽의 텍스트.
 *
 * @param page PDF 뷰어 기준 쪽 번호 (1부터)
 */
public record PageText(int page, String text) {
}
