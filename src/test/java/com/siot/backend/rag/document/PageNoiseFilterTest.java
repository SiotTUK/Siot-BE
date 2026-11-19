package com.siot.backend.rag.document;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageNoiseFilterTest {

    @Test
    void removesRepeatedHeaderFooterAndPageNumbers() {
        List<String> topics = List.of("외국인등록", "건강보험", "자동차세", "쓰레기 배출");
        List<PageText> pages = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            String topic = topics.get(i - 1);
            pages.add(new PageText(i, """
                    시흥시 외국인주민 생활안내 2026

                    %s 안내를 시작합니다.
                    문의: 시흥시청 031-310-2114
                    %s 안내를 마칩니다.

                    - %d -
                    """.formatted(topic, topic, i)));
        }

        List<PageText> cleaned = PageNoiseFilter.clean(pages);

        assertThat(cleaned).extracting(PageText::page).containsExactly(1, 2, 3, 4);
        assertThat(cleaned.get(2).text()).isEqualTo("""
                자동차세 안내를 시작합니다.
                문의: 시흥시청 031-310-2114
                자동차세 안내를 마칩니다.""");
    }

    @Test
    void keepsRepeatedLinesInTheMiddleOfPages() {
        List<PageText> pages = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            pages.add(new PageText(i, "첫 줄 " + "가나다".repeat(i) + "\n둘째 줄 " + i + "번\n※ 자세한 내용은 1345\n넷째 줄 " + "라".repeat(i) + "\n마지막 줄 " + "마".repeat(i)));
        }

        assertThat(PageNoiseFilter.clean(pages)).allSatisfy(page ->
                assertThat(page.text()).contains("※ 자세한 내용은 1345"));
    }

    @Test
    void shortDocumentKeepsHeaderButDropsPageNumber() {
        List<PageText> pages = List.of(
                new PageText(1, "외국인 생활 가이드\n첫 쪽 본문입니다.\n1 / 2"),
                new PageText(2, "외국인 생활 가이드\n둘째 쪽 본문입니다.\nPage 2"));

        List<PageText> cleaned = PageNoiseFilter.clean(pages);

        assertThat(cleaned.get(0).text()).isEqualTo("외국인 생활 가이드\n첫 쪽 본문입니다.");
        assertThat(cleaned.get(1).text()).isEqualTo("외국인 생활 가이드\n둘째 쪽 본문입니다.");
    }
}
