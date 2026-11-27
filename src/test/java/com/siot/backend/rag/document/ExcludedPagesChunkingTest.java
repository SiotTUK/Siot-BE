package com.siot.backend.rag.document;

import com.siot.backend.rag.document.ContentBlock.TableBlock;
import com.siot.backend.rag.document.ContentBlock.TableField;
import com.siot.backend.rag.document.ContentBlock.TableRow;
import com.siot.backend.rag.document.ContentBlock.TextLine;
import com.siot.backend.rag.document.DocumentChunker.Chunk;
import com.siot.backend.rag.document.ExtractedContent.Source;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** ADM-04: 적재에서 뺀 쪽. 남은 쪽 번호는 원본 그대로이고, 한 조각이 뺀 쪽의 앞뒤를 함께 담지 않는다 */
class ExcludedPagesChunkingTest {

    private final DocumentChunker chunker = new DocumentChunker();

    private static TextLine line(String text, int page) {
        return new TextLine(text, page, false);
    }

    private List<Chunk> split(Set<Integer> excluded, ContentBlock... blocks) {
        return chunker.split(new ExtractedContent(List.of(blocks), 5, Source.PLAIN, 1, excluded), "Guide");
    }

    @Test
    void paragraphDoesNotContinueAcrossExcludedPage() {
        TextLine before = line("The first part of the rule applies to students who", 1);
        TextLine after = line("work part time during the semester.", 3);

        assertThat(split(Set.of(), before, after))
                .extracting(Chunk::pageStart, Chunk::pageEnd)
                .containsExactly(tuple(1, 3));
        assertThat(split(Set.of(2), before, after))
                .extracting(Chunk::pageStart, Chunk::pageEnd)
                .containsExactly(tuple(1, 1), tuple(3, 3));
    }

    @Test
    void longTextIsNotOverlappedAcrossExcludedPage() {
        TextLine before = new TextLine("Alpha sentence about work permits. ".repeat(12).strip(), 1, true);
        TextLine after = new TextLine("Bravo sentence about school hours. ".repeat(12).strip(), 3, true);

        List<Chunk> chunks = split(Set.of(2), before, after);

        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.pageStart()).isEqualTo(chunk.pageEnd()));
        assertThat(chunks).filteredOn(chunk -> chunk.pageStart() == 3)
                .isNotEmpty()
                .allSatisfy(chunk -> assertThat(chunk.content()).doesNotContain("Alpha"));
    }

    @Test
    void tableRowsAreSplitAtExcludedPage() {
        TableBlock table = new TableBlock(List.of("Item", "Fee"), List.of(
                new TableRow(List.of(new TableField("Item", "Card"), new TableField("Fee", "30000")), 1),
                new TableRow(List.of(new TableField("Item", "Visa"), new TableField("Fee", "60000")), 3)));

        assertThat(split(Set.of(2), table))
                .extracting(Chunk::pageStart, Chunk::pageEnd)
                .containsExactly(tuple(1, 1), tuple(3, 3));
    }

    @Test
    void extractorKeepsOriginalPageNumbersAndNoChunkCrossesExcludedPage() throws IOException {
        byte[] pdf = TestPdfs.create(List.of(
                List.of("1. Part Time Work", "Students may work after six months of study."),
                List.of("Confirmation Form", "Students may work twenty hours a week."),
                List.of("The permit is attached to the passport.")));

        ExtractedContent content = new PdfContentExtractor().extract(pdf, Set.of(2));
        List<Chunk> chunks = chunker.split(content, "Guide");

        assertThat(content.excludedPages()).containsExactly(2);
        assertThat(chunks).noneSatisfy(chunk -> assertThat(chunk.content()).contains("twenty hours"));
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.pageStart()).isEqualTo(chunk.pageEnd());
            assertThat(chunk.pageStart()).isIn(1, 3);
        });
        assertThat(chunks).extracting(Chunk::pageStart).contains(1, 3);
    }
}
