package com.siot.backend.rag.document;

import com.siot.backend.rag.document.ContentBlock.TableBlock;
import com.siot.backend.rag.document.ContentBlock.TableRow;
import com.siot.backend.rag.document.ContentBlock.TextLine;
import com.siot.backend.rag.document.ExtractedContent.Source;
import com.siot.backend.rag.document.TaggedTestPdf.Cell;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;

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

    /** 머리 행 없이 같은 모양 행만 있는 표 (첫 행에 전화·URL·주소) */
    @Test
    void tableWithValuesInFirstRowHasNoHeader() throws IOException {
        byte[] pdf = TaggedTestPdf.create().page()
                .table(List.of(
                        List.of(Cell.of("EBS Durian"), Cell.of("www.ebs.co.kr/durian")),
                        List.of(Cell.of("Nuri Sejong"), Cell.of("www.sejonghakdang.org"))))
                .build();

        TableBlock table = (TableBlock) extractor.extract(pdf).blocks().getFirst();
        assertThat(table.columns()).isEmpty();
        assertThat(table.rows()).extracting(TableRow::render).containsExactly(
                "EBS Durian / www.ebs.co.kr/durian",
                "Nuri Sejong / www.sejonghakdang.org");
    }

    /** 첫 열이 항목 이름이고 첫 행에도 값(설명)이 있는 표 */
    @Test
    void tableWithDescriptiveFirstRowUsesFirstColumnAsLabels() throws IOException {
        byte[] pdf = TaggedTestPdf.create().page()
                .table(List.of(
                        List.of(Cell.of("Programs"), Cell.of("· Korean class · Culture"), Cell.of("· Counseling, jobs")),
                        List.of(Cell.of("Location"), Cell.of("Gunja-ro 381"), Cell.of("Gongdan 1-daero 259")),
                        List.of(Cell.of("Hours"), Cell.of("Mon-Fri 09:00-18:00"), Cell.of("Tue-Sun 09:00-18:00"))))
                .build();

        TableBlock table = (TableBlock) extractor.extract(pdf).blocks().getFirst();
        assertThat(table.columns()).isEmpty();
        assertThat(table.rows()).extracting(TableRow::render).containsExactly(
                "Programs: · Korean class · Culture / · Counseling, jobs",
                "Location: Gunja-ro 381 / Gongdan 1-daero 259",
                "Hours: Mon-Fri 09:00-18:00 / Tue-Sun 09:00-18:00");
    }

    /** 첫 데이터 행은 빈 칸, 합쳐진 칸(RowSpan)의 글자는 둘째 데이터 행에 태그된 표 (InDesign) */
    @Test
    void fillsMergedCellTaggedInSecondRowIntoFirstDataRow() throws IOException {
        byte[] pdf = TaggedTestPdf.create().page()
                .table(List.of(
                        List.of(Cell.of("Department"), Cell.of("Team"), Cell.of("Phone")),
                        List.of(Cell.of(""), Cell.of("Policy team"), Cell.of("3008")),
                        List.of(new Cell("Foreign Residents", 2, 1), Cell.of("Support team"), Cell.of("2642")),
                        List.of(Cell.of("Family team"), Cell.of("2611")),
                        List.of(Cell.of("Library"), Cell.of("Kids team"), Cell.of("5230"))))
                .build();

        TableBlock table = (TableBlock) extractor.extract(pdf).blocks().getFirst();
        assertThat(table.rows()).extracting(TableRow::render).containsExactly(
                "Department: Foreign Residents / Team: Policy team / Phone: 3008",
                "Department: Foreign Residents / Team: Support team / Phone: 2642",
                "Department: Foreign Residents / Team: Family team / Phone: 2611",
                "Department: Library / Team: Kids team / Phone: 5230");
    }

    @Test
    void leavesOutExcludedPages() throws IOException {
        byte[] tagged = TaggedTestPdf.create()
                .page().paragraph("CONTENTS")
                .page().paragraph("1-1 Basic Rules")
                .table(List.of(
                        List.of(Cell.of("Type"), Cell.of("Fine")),
                        List.of(Cell.of("Parking"), Cell.of("40,000 won"))))
                .build();
        byte[] plain = TestPdfs.create(List.of(List.of("Cover page"), List.of("Register within 90 days.")));

        assertThat(extractor.extract(tagged, Set.of(1)).blocks())
                .hasSize(2)
                .first().isEqualTo(new TextLine("1-1 Basic Rules", 2, true));
        assertThat(extractor.extract(tagged, Set.of(2)).blocks())
                .containsExactly(new TextLine("CONTENTS", 1, true));
        assertThat(extractor.extract(plain, Set.of(1)).blocks())
                .containsExactly(new TextLine("Register within 90 days.", 2, false));
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
