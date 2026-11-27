package com.siot.backend.rag.document;

import com.siot.backend.rag.document.ContentBlock.TextLine;
import com.siot.backend.rag.document.ExtractedContent.Source;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * ADM-04 [EXTRACT]: 태그 PDF 는 구조 트리({@link TaggedPdfExtractor}), 아니면 화면 위치 순서({@link PdfTextExtractor}).
 * 태그가 일부만 된 PDF 에서 내용을 잃지 않도록, 구조 트리 글자 수가 화면 위치 방식의
 * {@value #MIN_TAGGED_COVERAGE} 배 미만이면 화면 위치 방식을 쓴다.
 */
@Slf4j
public class PdfContentExtractor {

    static final double MIN_TAGGED_COVERAGE = 0.8;

    private final PdfTextExtractor plainExtractor;
    private final TaggedPdfExtractor taggedExtractor;

    public PdfContentExtractor() {
        this(new PdfTextExtractor(), new TaggedPdfExtractor());
    }

    PdfContentExtractor(PdfTextExtractor plainExtractor, TaggedPdfExtractor taggedExtractor) {
        this.plainExtractor = plainExtractor;
        this.taggedExtractor = taggedExtractor;
    }

    /** @throws IOException PDF 를 열 수 없음 (손상·암호) */
    public ExtractedContent extract(byte[] pdf) throws IOException {
        return extract(pdf, Set.of());
    }

    /**
     * @param excludedPages 적재에서 뺄 쪽 (표지·목차·빈칸 서식 등, ADM-04). 추출 방식 판단(비율)은 전체 쪽으로 하고, 결과에서만 뺀다
     */
    public ExtractedContent extract(byte[] pdf, Set<Integer> excludedPages) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            // 머리말·꼬리말 반복 판단은 전체 쪽으로 한다
            List<PageText> raw = plainExtractor.extractRaw(document);
            List<ContentBlock> plain = toBlocks(PageNoiseFilter.clean(raw));
            int pageCount = document.getNumberOfPages();
            if (TaggedPdfExtractor.isTagged(document)) {
                TaggedPdfExtractor.Result tagged = taggedExtractor.extract(document);
                int taggedLength = tagged.usedLength();
                // 비교 기준: 화면의 모든 글자에서 머리말·꼬리말(Artifact)을 뺀 것
                int contentLength = ExtractedContent.nonSpaceLength(toBlocks(raw)) - tagged.artifactLength();
                double coverage = contentLength <= 0 ? (taggedLength > 0 ? 1 : 0) : (double) taggedLength / contentLength;
                if (taggedLength > 0 && coverage >= MIN_TAGGED_COVERAGE) {
                    // 구조 트리에 Artifact 로 표시되지 않은 머리말·꼬리말 ("28 비자 내비게이터")
                    List<ContentBlock> blocks = withoutRepeatedLines(tagged.blocks(), PageNoiseFilter.repeatedLines(raw));
                    return new ExtractedContent(withoutPages(blocks, excludedPages), pageCount, Source.TAGGED,
                            coverage, excludedPages);
                }
                log.info("Tagged PDF structure covers only {}% of the text, using plain extraction",
                        Math.round(coverage * 100));
                return new ExtractedContent(withoutPages(plain, excludedPages), pageCount, Source.PLAIN, coverage,
                        excludedPages);
            }
            return new ExtractedContent(withoutPages(plain, excludedPages), pageCount, Source.PLAIN, 1, excludedPages);
        }
    }

    /** 뺄 쪽의 줄·표 행을 지운다 (행이 모두 빠진 표는 표째 지운다) */
    static List<ContentBlock> withoutPages(List<ContentBlock> blocks, Set<Integer> excludedPages) {
        if (excludedPages.isEmpty()) {
            return blocks;
        }
        List<ContentBlock> kept = new ArrayList<>(blocks.size());
        for (ContentBlock block : blocks) {
            switch (block) {
                case TextLine line -> {
                    if (!excludedPages.contains(line.page())) {
                        kept.add(line);
                    }
                }
                case ContentBlock.TableBlock table -> {
                    List<ContentBlock.TableRow> rows = table.rows().stream()
                            .filter(row -> !excludedPages.contains(row.page()))
                            .toList();
                    if (!rows.isEmpty()) {
                        kept.add(new ContentBlock.TableBlock(table.columns(), rows));
                    }
                }
            }
        }
        return kept;
    }

    /** 반복 머리말·꼬리말과 같은 줄을 뺀다 (표는 그대로) */
    static List<ContentBlock> withoutRepeatedLines(List<ContentBlock> blocks, Set<String> repeated) {
        if (repeated.isEmpty()) {
            return blocks;
        }
        List<ContentBlock> kept = new ArrayList<>(blocks.size());
        for (ContentBlock block : blocks) {
            if (block instanceof TextLine line && PageNoiseFilter.isRepeatedLine(line.text(), repeated)) {
                continue;
            }
            kept.add(block);
        }
        return kept;
    }

    /** 쪽 텍스트를 줄 단위로. 빈 줄 다음 줄이 새 문단. 줄 모양(글자 크기·굵기)을 알면 함께 싣는다 */
    public static List<ContentBlock> toBlocks(List<PageText> pages) {
        List<ContentBlock> blocks = new ArrayList<>();
        boolean blank = true;
        for (PageText page : pages) {
            for (String raw : page.text().split("\n")) {
                String text = PdfTextExtractor.styleKey(raw);
                if (text.isEmpty()) {
                    blank = true;
                    continue;
                }
                PageText.LineStyle style = page.styles().get(text);
                blocks.add(style == null
                        ? new TextLine(text, page.page(), blank)
                        : new TextLine(text, page.page(), blank, style.fontSize(), style.bold(), style.fullWidth()));
                blank = false;
            }
        }
        return blocks;
    }
}
