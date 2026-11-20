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

    /** 05: 절 제목 옆의 "󰁾 목차" 링크는 쪽 중간에도 있고, 화면 순서로 읽으면 아이콘과 글자가 떨어지기도 한다 */
    @Test
    void removesIconLinksAnywhereButKeepsIconBullets() {
        String tocIcon = "󰁾"; // U+F007E
        // 02: 같은 아이콘(U+F000)이 여러 제목 앞에 기호로 쓰인다
        List<String> iconTitles = List.of("외국인 기술자 소득세 감면", "외국인 교사 소득세 면제", "자녀세액공제 금액 확대",
                "해외 우수인재 소득세 감면", "소득공제 적용 여부");
        List<PageText> pages = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            String link = i == 3
                    ? tocIcon + " ②외교통 상부 추천서 ③대사관의 협조공문 또는 목차 고유번호증"   // 아이콘과 글자가 떨어짐
                    : tocIcon + " 목차 " + i + ". 무사증 입국자 체류자격 변경허가";
            pages.add(new PageText(i, "첫 줄 " + "가".repeat(i) + "\n본문 " + i + "번 내용입니다.\n" + link
                    + "\n " + iconTitles.get(i % 5) + "\n둘째 본문 " + i + "번입니다.\n마지막 줄 " + "다".repeat(i)));
        }

        List<PageText> cleaned = PageNoiseFilter.clean(pages);

        assertThat(cleaned).allSatisfy(page -> assertThat(page.text()).doesNotContain(tocIcon, "목차"));
        assertThat(cleaned.get(0).text()).contains("1. 무사증 입국자 체류자격 변경허가");
        assertThat(cleaned.get(2).text()).contains("③대사관의 협조공문 또는 고유번호증");
        // 여러 제목 앞에 기호로 쓰인 아이콘은 남는다
        assertThat(cleaned.get(0).text()).contains(" 외국인 교사 소득세 면제");
        assertThat(cleaned.get(4).text()).contains(" 외국인 기술자 소득세 감면");
    }

    /** 줄이 많은 쪽은 위·아래 3줄을 보고, 전체 쪽의 30% 이상에 나오면 머리말·꼬리말 (03 "28 비자 내비게이터") */
    @Test
    void removesFooterRepeatedOnThirtyPercentOfPagesWithinThreeEdgeLines() {
        List<PageText> pages = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            StringBuilder text = new StringBuilder();
            for (int line = 1; line <= 6; line++) {
                text.append(i).append("쪽 ").append(line).append("번째 본문 ").append("가".repeat(line)).append('\n');
            }
            if (i % 3 == 0) {
                text.append(i * 2).append(" 비자 내비게이터\n");   // 꼬리말 (쪽 번호와 붙음)
            }
            text.append("끝 줄 ").append("나".repeat(i)).append('\n').append("- ").append(i).append(" -");
            pages.add(new PageText(i, text.toString()));
        }

        List<PageText> cleaned = PageNoiseFilter.clean(pages);

        assertThat(cleaned).allSatisfy(page -> assertThat(page.text()).doesNotContain("비자 내비게이터", " -"));
        assertThat(cleaned.get(2).text()).contains("3쪽 6번째 본문", "끝 줄");
    }

    /** 태그 PDF 는 구조 트리에서 꺼낸 줄을 반복 머리말·꼬리말과 비교한다 (숫자는 무시) */
    @Test
    void matchesTaggedLinesAgainstRepeatedLines() {
        List<PageText> pages = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            pages.add(new PageText(i, "본문 " + "가".repeat(i) + " 내용입니다.\n둘째 줄 " + "나".repeat(i) + "\n" + (i + 26) + " 비자 내비게이터"));
        }
        var repeated = PageNoiseFilter.repeatedLines(pages);

        assertThat(PageNoiseFilter.isRepeatedLine("비자 내비게이터", repeated)).isTrue();
        assertThat(PageNoiseFilter.isRepeatedLine("28 비자 내비게이터", repeated)).isTrue();
        assertThat(PageNoiseFilter.isRepeatedLine("비자 내비게이터란 무엇인가", repeated)).isFalse();
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
