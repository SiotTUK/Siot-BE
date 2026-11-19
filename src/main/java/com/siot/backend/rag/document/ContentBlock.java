package com.siot.backend.rag.document;

import java.util.List;
import java.util.stream.Collectors;

/**
 * PDF 에서 추출한 내용 단위 (읽는 순서대로). {@link DocumentChunker} 가 조각으로 나눈다.
 */
public sealed interface ContentBlock permits ContentBlock.TextLine, ContentBlock.TableBlock {

    /**
     * 텍스트 한 줄. 태그 PDF 에서는 문단 하나(쪽이 바뀌면 쪽마다 하나), 태그 없는 PDF 에서는 추출한 줄 하나.
     *
     * @param page         PDF 뷰어 기준 쪽 번호 (1부터)
     * @param newParagraph 새 문단의 시작 (태그 PDF 의 문단 경계, 태그 없는 PDF 의 빈 줄 뒤)
     */
    record TextLine(String text, int page, boolean newParagraph) implements ContentBlock {
    }

    /**
     * 표 (태그 PDF 의 Table). 합쳐진 칸(RowSpan)은 아래 행에도 채워져 있다.
     *
     * @param columns 머리 행에서 얻은 열 이름 (머리 행이 없으면 빈 목록)
     * @param rows    머리 행을 뺀 행
     */
    record TableBlock(List<String> columns, List<TableRow> rows) implements ContentBlock {

        public int firstPage() {
            return rows.stream().mapToInt(TableRow::page).min().orElse(1);
        }
    }

    /** @param page 이 행이 있는 쪽 */
    record TableRow(List<TableField> fields, int page) {

        /** "구분: 방문 납부 / 납부 방법: … / 비고: …" */
        public String render() {
            return fields.stream().map(TableField::render).collect(Collectors.joining(" / "));
        }
    }

    /** @param name 열 이름 (없으면 null) */
    record TableField(String name, String value) {

        String render() {
            return name == null || name.isBlank() ? value : name + ": " + value;
        }
    }
}
