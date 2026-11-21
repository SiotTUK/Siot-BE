package com.siot.backend.rag.document;

import com.siot.backend.rag.document.ContentBlock.TableBlock;
import com.siot.backend.rag.document.ContentBlock.TableField;
import com.siot.backend.rag.document.ContentBlock.TableRow;
import com.siot.backend.rag.document.ContentBlock.TextLine;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractedContentTest {

    /** 머리 행의 일부 칸이 비어 있는 표: 그 열 이름은 null 로 온다 */
    @Test
    void tableWithBlankHeaderCellIsNotEmpty() {
        TableBlock table = new TableBlock(Arrays.asList("구분", null),
                List.of(new TableRow(List.of(new TableField("구분", "방문 납부"), new TableField(null, "은행 창구")), 3)));

        ExtractedContent content = new ExtractedContent(List.of(table), 3, ExtractedContent.Source.TAGGED, 1);

        assertThat(content.isEmpty()).isFalse();
        assertThat(ExtractedContent.nonSpaceLength(List.of(table))).isEqualTo("구분방문납부은행창구".length());
    }

    @Test
    void blankTextIsEmpty() {
        ExtractedContent content = new ExtractedContent(List.of(new TextLine("  \n ", 1, true)), 1,
                ExtractedContent.Source.PLAIN, 1);

        assertThat(content.isEmpty()).isTrue();
    }
}
