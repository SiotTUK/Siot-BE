package com.siot.backend.rag.document;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfTextExtractorTest {

    private final PdfTextExtractor extractor = new PdfTextExtractor();

    @Test
    void extractsTextPerPageWithoutHeaderFooterAndPageNumber() throws IOException {
        byte[] pdf = TestPdfs.create(List.of(
                List.of("Siheung Living Guide 2026", "", "Alien registration is required within 90 days.", "", "- 1 -"),
                List.of("Siheung Living Guide 2026", "", "Health insurance starts after six months.", "", "- 2 -"),
                List.of("Siheung Living Guide 2026", "", "Car tax is paid in June and December.", "", "- 3 -")));

        List<PageText> pages = extractor.extract(pdf);

        assertThat(pages).extracting(PageText::page).containsExactly(1, 2, 3);
        assertThat(pages.get(1).text()).isEqualTo("Health insurance starts after six months.");
        assertThat(pages).allSatisfy(page -> assertThat(page.text()).doesNotContain("Siheung Living Guide", "- "));
    }

    @Test
    void blankPdfHasNoText() throws IOException {
        assertThat(extractor.extract(TestPdfs.blank(2))).extracting(PageText::text).containsOnly("");
    }

    @Test
    void rejectsNonPdf() {
        assertThatThrownBy(() -> extractor.extract("not a pdf".getBytes())).isInstanceOf(IOException.class);
    }
}
