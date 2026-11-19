package com.siot.backend.rag.document;

import com.siot.backend.rag.document.ContentBlock.TableBlock;
import com.siot.backend.rag.document.ContentBlock.TableRow;
import com.siot.backend.rag.document.ContentBlock.TextLine;
import com.siot.backend.rag.document.ExtractedContent.Source;
import com.siot.backend.rag.document.TaggedTestPdf.Cell;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TaggedPdfExtractorTest {

    private final PdfContentExtractor extractor = new PdfContentExtractor();

    @Test
    void readsStructureOrderWithoutArtifacts() throws IOException {
        byte[] pdf = TaggedTestPdf.create().page()
                .artifact("2025 Siheung Living Guide 11")
                .paragraph("1-4 Local Tax")
                .paragraph("Property tax is paid in July and September.")
                .artifact("KOREAN ENGLISH")
                .build();

        ExtractedContent content = extractor.extract(pdf);

        assertThat(content.source()).isEqualTo(Source.TAGGED);
        assertThat(content.pageCount()).isEqualTo(1);
        assertThat(content.blocks()).containsExactly(
                new TextLine("1-4 Local Tax", 1, true),
                new TextLine("Property tax is paid in July and September.", 1, true));
    }

    @Test
    void splitsParagraphAcrossPagesByPage() throws IOException {
        byte[] pdf = TaggedTestPdf.create()
                .page().paragraph("Foreign residents join health insurance")
                .page().continueParagraph("after six months of stay.")
                .build();

        assertThat(extractor.extract(pdf).blocks()).containsExactly(
                new TextLine("Foreign residents join health insurance", 1, true),
                new TextLine("after six months of stay.", 2, false));
    }

    @Test
    void restoresSpacesFromGlyphGaps() throws IOException {
        byte[] pdf = TaggedTestPdf.create().page()
                .paragraphWithoutSpaceGlyphs("Visit", "the", "immigration", "office")
                .build();

        assertThat(extractor.extract(pdf).blocks())
                .containsExactly(new TextLine("Visit the immigration office", 1, true));
    }

    @Test
    void readsTableRowsWithColumnNamesAndSpans() throws IOException {
        byte[] pdf = TaggedTestPdf.create().page()
                .paragraph("Welfare center programs")
                .table(List.of(
                        List.of(Cell.of("Type"), Cell.of("Program"), Cell.of("Target")),
                        List.of(new Cell("Korean class", 2, 1), Cell.of("Weekend class"), Cell.of("Adults")),
                        List.of(Cell.of("Weekday class"), Cell.of("Workers")),
                        List.of(Cell.of("Counseling"), new Cell("Multilingual counseling", 1, 2))))
                .paragraph("Call 031-434-0411 for details.")
                .build();

        List<ContentBlock> blocks = extractor.extract(pdf).blocks();

        assertThat(blocks).hasSize(3);
        TableBlock table = (TableBlock) blocks.get(1);
        assertThat(table.columns()).containsExactly("Type", "Program", "Target");
        assertThat(table.rows()).extracting(TableRow::render).containsExactly(
                "Type: Korean class / Program: Weekend class / Target: Adults",
                "Type: Korean class / Program: Weekday class / Target: Workers",
                "Type: Counseling / Program: Multilingual counseling");
        assertThat(table.rows()).extracting(TableRow::page).containsOnly(1);
        assertThat(blocks.get(2)).isEqualTo(new TextLine("Call 031-434-0411 for details.", 1, true));
    }

    @Test
    void singleRowTableHasNoColumnNames() throws IOException {
        byte[] pdf = TaggedTestPdf.create().page()
                .table(List.of(List.of(Cell.of("Siheung City Hall"), Cell.of("031-310-2114"))))
                .build();

        TableBlock table = (TableBlock) extractor.extract(pdf).blocks().getFirst();
        assertThat(table.columns()).isEmpty();
        assertThat(table.rows()).extracting(TableRow::render).containsExactly("Siheung City Hall / 031-310-2114");
    }

    @Test
    void fallsBackToPlainWhenStructureCoversLittleText() throws IOException {
        byte[] pdf = TaggedTestPdf.create().page()
                .paragraph("Tagged line.")
                .untagged("This long paragraph was not tagged by the authoring tool at all.")
                .untagged("Neither was this second paragraph with more important text.")
                .build();

        ExtractedContent content = extractor.extract(pdf);

        assertThat(content.source()).isEqualTo(Source.PLAIN);
        assertThat(content.coverage()).isLessThan(PdfContentExtractor.MIN_TAGGED_COVERAGE);
        assertThat(content.blocks()).extracting(block -> ((TextLine) block).text())
                .contains("Neither was this second paragraph with more important text.");
    }

    @Test
    void untaggedPdfUsesPlainExtraction() throws IOException {
        byte[] pdf = TestPdfs.create(List.of(List.of("1. Alien Registration", "Register within 90 days.")));

        ExtractedContent content = extractor.extract(pdf);

        assertThat(content.source()).isEqualTo(Source.PLAIN);
        assertThat(content.blocks()).containsExactly(
                new TextLine("1. Alien Registration", 1, true),
                new TextLine("Register within 90 days.", 1, false));
    }
}
