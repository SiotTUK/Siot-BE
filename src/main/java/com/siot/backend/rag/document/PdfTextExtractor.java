package com.siot.backend.rag.document;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * ADM-04 [EXTRACT]: PDF 쪽별 텍스트 추출 (PDFBox). 반복되는 머리말·꼬리말과 쪽 번호 줄은 {@link PageNoiseFilter} 로 뺀다.
 * 스캔 이미지 PDF 는 텍스트가 없으므로 빈 결과가 된다 (OCR 은 하지 않는다).
 */
public class PdfTextExtractor {

    /** @throws IOException PDF 를 열 수 없음 (손상·암호) */
    public List<PageText> extract(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            // 2단 편집·표에서 읽는 순서가 섞이지 않도록 화면 위치 순으로 읽는다
            stripper.setSortByPosition(true);
            stripper.setLineSeparator("\n");
            List<PageText> pages = new ArrayList<>(document.getNumberOfPages());
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                pages.add(new PageText(page, stripper.getText(document)));
            }
            return PageNoiseFilter.clean(pages);
        }
    }
}
