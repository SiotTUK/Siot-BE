package com.siot.backend.rag.document;

import java.util.List;
import java.util.Set;

/**
 * PDF 추출 결과.
 *
 * @param blocks    읽는 순서대로의 텍스트·표
 * @param pageCount 전체 쪽 수
 * @param source    TAGGED(구조 트리) / PLAIN(화면 위치 순서)
 * @param coverage  구조 트리 글자 수 ÷ 화면 위치 방식 글자 수 (공백 제외). PLAIN 이면 확인한 값 또는 1
 * @param excludedPages 적재에서 뺀 쪽. blocks 에는 이 쪽의 내용이 없고, 남은 쪽 번호는 원본 PDF 그대로다
 */
public record ExtractedContent(List<ContentBlock> blocks, int pageCount, Source source, double coverage,
                               Set<Integer> excludedPages) {

    /** 뺀 쪽이 없는 결과 */
    public ExtractedContent(List<ContentBlock> blocks, int pageCount, Source source, double coverage) {
        this(blocks, pageCount, source, coverage, Set.of());
    }

    public enum Source {
        /** 태그 PDF 구조 트리 (머리말·꼬리말 Artifact 제외, 표는 행 단위) */
        TAGGED,
        /** PDFTextStripper + PageNoiseFilter */
        PLAIN
    }

    /** 텍스트가 하나도 없음 (스캔 이미지 PDF 등) */
    public boolean isEmpty() {
        return nonSpaceLength(blocks) == 0;
    }

    static int nonSpaceLength(List<ContentBlock> blocks) {
        int length = 0;
        for (ContentBlock block : blocks) {
            switch (block) {
                case ContentBlock.TextLine line -> length += nonSpaceLength(line.text());
                case ContentBlock.TableBlock table -> {
                    for (ContentBlock.TableRow row : table.rows()) {
                        for (ContentBlock.TableField field : row.fields()) {
                            length += nonSpaceLength(field.value());
                        }
                    }
                    for (String column : table.columns()) {
                        length += nonSpaceLength(column);
                    }
                }
            }
        }
        return length;
    }

    /** 머리 행이 빈 열의 이름은 null 이다 (TaggedPdfExtractor.columnNames) */
    static int nonSpaceLength(String text) {
        if (text == null) {
            return 0;
        }
        int length = 0;
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                length++;
            }
        }
        return length;
    }
}
