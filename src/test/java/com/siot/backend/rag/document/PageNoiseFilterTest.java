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

    /** 책자: 홀수 쪽과 짝수 쪽의 꼬리말이 다르고, 표지에는 없으며, 본문 줄 끝에 붙어 나오기도 한다 */
    @Test
    void removesOddAndEvenPageFootersSeparately() {
        List<String> departments = List.of("민원여권과", "세정과", "시세관리과", "교통정책과", "자원순환과",
                "동물축산과", "교육자치과", "아동돌봄과");
        List<PageText> pages = new ArrayList<>();
        pages.add(new PageText(1, "어서와~ 시흥은 처음이지?\n2025 Siheung-si Guidebook"));
        for (int i = 2; i <= 9; i++) {
            String footer = i % 2 == 1
                    ? "2025 시흥시 외국인주민 생활안내 " + i + "\nKOREAN ENGLISH"
                    : i + " 어서와~ 시흥은 처음이지?";
            String body = "■ 안내 " + "가나다라마".substring(0, 1 + i % 5) + "\n본문 " + "바".repeat(i) + " 내용입니다.\n"
                    + "· 문의 : 시흥시청 " + departments.get(i - 2) + " ☎(031)310-20" + i;
            pages.add(new PageText(i, i == 4
                    ? body + " " + footer   // 꼬리말이 마지막 본문 줄에 붙어 나온 쪽
                    : body + "\n" + footer));
        }

        List<PageText> cleaned = PageNoiseFilter.clean(pages);

        assertThat(cleaned.subList(1, cleaned.size())).allSatisfy(page -> assertThat(page.text())
                .doesNotContain("KOREAN", "처음이지", "생활안내")
                .contains("■ 안내", "· 문의 : 시흥시청"));
        assertThat(cleaned.get(3).text()).endsWith("· 문의 : 시흥시청 시세관리과 ☎(031)310-204");
        assertThat(cleaned.get(0).text()).isEqualTo("어서와~ 시흥은 처음이지?\n2025 Siheung-si Guidebook");
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
