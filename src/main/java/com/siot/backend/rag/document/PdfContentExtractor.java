package com.siot.backend.rag.document;

import com.siot.backend.rag.document.ContentBlock.TextLine;
import com.siot.backend.rag.document.ExtractedContent.Source;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
        try (PDDocument document = Loader.loadPDF(pdf)) {
            List<PageText> raw = plainExtractor.extractRaw(document);
            List<ContentBlock> plain = toBlocks(PageNoiseFilter.clean(raw));
            if (TaggedPdfExtractor.isTagged(document)) {
                TaggedPdfExtractor.Result tagged = taggedExtractor.extract(document);
                int taggedLength = tagged.usedLength();
                // 비교 기준: 화면의 모든 글자에서 머리말·꼬리말(Artifact)을 뺀 것
                int contentLength = ExtractedContent.nonSpaceLength(toBlocks(raw)) - tagged.artifactLength();
                double coverage = contentLength <= 0 ? (taggedLength > 0 ? 1 : 0) : (double) taggedLength / contentLength;
                if (taggedLength > 0 && coverage >= MIN_TAGGED_COVERAGE) {
                    return new ExtractedContent(tagged.blocks(), document.getNumberOfPages(), Source.TAGGED, coverage);
                }
                log.info("Tagged PDF structure covers only {}% of the text, using plain extraction",
                        Math.round(coverage * 100));
                return new ExtractedContent(plain, document.getNumberOfPages(), Source.PLAIN, coverage);
            }
            return new ExtractedContent(plain, document.getNumberOfPages(), Source.PLAIN, 1);
        }
    }

    /** 쪽 텍스트를 줄 단위로. 빈 줄 다음 줄이 새 문단 */
    public static List<ContentBlock> toBlocks(List<PageText> pages) {
        List<ContentBlock> blocks = new ArrayList<>();
        boolean blank = true;
        for (PageText page : pages) {
            for (String raw : page.text().split("\n")) {
                String text = raw.replace(' ', ' ').strip();
                if (text.isEmpty()) {
                    blank = true;
                    continue;
                }
                blocks.add(new TextLine(text, page.page(), blank));
                blank = false;
            }
        }
        return blocks;
    }
}
