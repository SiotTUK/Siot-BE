package com.siot.backend.rag.document;

import com.siot.backend.rag.document.ContentBlock.TableBlock;
import com.siot.backend.rag.document.ContentBlock.TableField;
import com.siot.backend.rag.document.ContentBlock.TableRow;
import com.siot.backend.rag.document.ContentBlock.TextLine;
import com.siot.backend.rag.document.DocumentChunker.Chunk;
import com.siot.backend.rag.document.ExtractedContent.Source;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentChunkerTest {

    private static final String DOC = "시흥시 외국인주민 생활안내";

    private final DocumentChunker chunker = new DocumentChunker();

    @Test
    void startsNewChunkPerSectionWithTitleOnFirstLine() {
        List<Chunk> chunks = chunker.split(List.of(page(1, """
                1. 외국인등록
                외국인등록은 입국일부터 90일 이내에 출입국·외국인청에 신청해야 합니다.
                2. 체류지 변경 신고
                이사한 날부터 15일 이내에 새 주소지 행정복지센터에 신고합니다.
                """)), DOC);

        assertThat(chunks).extracting(Chunk::title).containsExactly("1. 외국인등록", "2. 체류지 변경 신고");
        assertThat(chunks).extracting(Chunk::chunkNo).containsExactly(1, 2);
        assertThat(chunks.getFirst().content()).startsWith("1. 외국인등록\n외국인등록은");
    }

    @Test
    void numberedListItemsAreNotHeadings() {
        List<Chunk> chunks = chunker.split(List.of(page(1, """
                준비물은 다음과 같습니다.
                1. 여권
                2. 사진 1장
                3. 수수료 3만 원
                신청은 출입국·외국인청에서 할 수 있으며 방문 예약이 필요합니다.
                """)), DOC);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().title()).isEqualTo(DOC);
        assertThat(chunks.getFirst().content()).contains("1. 여권", "2. 사진 1장", "3. 수수료 3만 원");
    }

    @Test
    void shortLineFollowedByShortLineIsNotHeading() {
        List<Chunk> chunks = chunker.split(List.of(page(1, """
                필요한 서류를 준비합니다.
                여권
                사진
                신청서는 창구에서 받을 수 있습니다.
                """)), DOC);

        assertThat(chunks).extracting(Chunk::title).containsOnly(DOC);
    }

    @Test
    void joinsParentAndChildHeadings() {
        List<Chunk> chunks = chunker.split(List.of(page(1, """
                2. 외국인등록
                가. 신청 기한
                입국일부터 90일 이내에 신청해야 합니다.
                나. 준비물
                여권, 사진 1장, 수수료 3만 원이 필요합니다.
                """)), DOC);

        assertThat(chunks).extracting(Chunk::title)
                .containsExactly("2. 외국인등록 > 가. 신청 기한", "2. 외국인등록 > 나. 준비물");
    }

    @Test
    void squareAndSectionNumberHeadingsAfterListLines() {
        List<Chunk> chunks = chunker.split(List.of(page(11, """
                1-4 지방세 납부
                ■ 재산세
                · 납부대상 : 매년 6월 1일을 기준으로 주택, 건축물, 토지, 선박을 소유하고 있는 자
                · 문의 : 시흥시청 시세관리과 ☎(031)310-2081~8
                ■ 주민세
                · 납부대상 : 매년 7월 1일을 기준으로 외국인등록 1년 경과 및 시흥시에 주소를 둔 외국인주민
                · 문의 : 시흥시청 시세관리과 ☎(031)310-2089
                """)), DOC);

        assertThat(chunks).extracting(Chunk::title)
                .containsExactly("1-4 지방세 납부 > ■ 재산세", "1-4 지방세 납부 > ■ 주민세");
        assertThat(chunks.get(0).content()).doesNotContain("주민세");
    }

    @Test
    void wrappedListLineIsNotHeading() {
        List<Chunk> chunks = chunker.split(List.of(page(11, """
                ■ 자동차세
                · 납부대상 : 각 기분의 납기가 있는 달의 1일(6월 1일 및 12월 1일) 현재 등록된 자동차, 이륜차,
                건설기계가 있는 자
                · 문의 : 시흥시청 시세관리과 ☎(031)310-2093~4
                """)), DOC);

        assertThat(chunks).extracting(Chunk::title).containsExactly("■ 자동차세");
        assertThat(chunks.getFirst().content()).contains("이륜차, 건설기계가 있는 자");
    }

    @Test
    void titleKeepsThreeLevels() {
        List<Chunk> chunks = chunker.splitBlocks(List.of(
                new TextLine("3-2 복지", 24, true),
                new TextLine("■ 아동·가족 복지", 24, true),
                new TextLine("(2) 공동육아나눔터", 25, true),
                new TextLine("부모와 아이가 함께 이용하는 공간입니다.", 25, true)), DOC);

        assertThat(chunks).extracting(Chunk::title)
                .containsExactly("3-2 복지 > ■ 아동·가족 복지 > (2) 공동육아나눔터");
    }

    @Test
    void upperSectionChangeResetsLowerTitle() {
        List<Chunk> chunks = chunker.split(List.of(
                page(11, """
                        1-4 지방세 납부
                        ■ 주민세
                        · 문의 : 시흥시청 시세관리과 ☎(031)310-2089
                        """),
                page(12, """
                        1-5 기타 생활편의
                        시루는 시흥시에서 발행하는 지역화폐로, 지역 내 가맹점에서 현금처럼 사용할 수 있습니다.
                        ■ 시흥화폐 '시루'
                        · 유형 : 종이 / 모바일 / 카드형
                        """)), DOC);

        assertThat(chunks).extracting(Chunk::title).containsExactly(
                "1-4 지방세 납부 > ■ 주민세", "1-5 기타 생활편의", "1-5 기타 생활편의 > ■ 시흥화폐 '시루'");
    }

    @Test
    void firstLineOfPageCanBeHeading() {
        List<Chunk> chunks = chunker.split(List.of(
                page(1, "· 문의 : 시흥시청 민원여권과 ☎(031)310-3150"),
                page(2, """
                        체류지 변경 신고
                        이사한 날부터 15일 이내에 새 주소지 행정복지센터에 신고합니다.
                        """)), DOC);

        assertThat(chunks).extracting(Chunk::title).containsExactly(DOC, "체류지 변경 신고");
    }

    @Test
    void keepsSizeLimitsAndOverlapsWithinSection() {
        String sentences = IntStream.rangeClosed(1, 60)
                .mapToObj(i -> "생활 안내 " + i + "번 문장은 외국인 주민이 알아야 할 내용을 설명합니다.")
                .collect(Collectors.joining(" "));
        List<Chunk> chunks = chunker.split(List.of(page(1, "1. 생활 안내\n" + sentences)), DOC);

        assertThat(chunks).hasSizeGreaterThan(2);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.content().length()).isLessThanOrEqualTo(DocumentChunker.MAX);
            assertThat(chunk.content()).startsWith("1. 생활 안내\n");
            assertThat(chunk.content()).endsWith("설명합니다.");
        });
        assertThat(chunks.getFirst().content().length()).isBetween(400, 700);
        for (int i = 1; i < chunks.size(); i++) {
            String previous = body(chunks.get(i - 1));
            String next = body(chunks.get(i));
            // 다음 조각의 앞부분(문장 단위)이 앞 조각의 끝과 같다
            String overlap = "";
            for (int end = next.indexOf("설명합니다."); end >= 0; end = next.indexOf("설명합니다.", end + 1)) {
                String prefix = next.substring(0, end + "설명합니다.".length());
                if (previous.endsWith(prefix)) {
                    overlap = prefix;
                }
            }
            assertThat(overlap).as("overlap between chunk %d and %d", i, i + 1).isNotEmpty();
            assertThat(overlap.length()).isLessThanOrEqualTo(DocumentChunker.OVERLAP);
        }
    }

    @Test
    void cutsVeryLongSentenceUnderMax() {
        String longSentence = IntStream.range(0, 400).mapToObj(i -> "단어" + i).collect(Collectors.joining(" "));
        List<Chunk> chunks = chunker.split(List.of(page(1, longSentence)), DOC);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk ->
                assertThat(chunk.content().length()).isLessThanOrEqualTo(DocumentChunker.MAX));
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.content()).doesNotContainPattern("단어\\d+단어"));
    }

    @Test
    void recordsPageRangeAcrossPages() {
        List<Chunk> chunks = chunker.split(List.of(
                page(1, "1. 건강보험\n외국인 주민도 6개월 이상 체류하면 건강보험에"),
                page(2, "자동으로 가입됩니다. 보험료는 매달 납부합니다."),
                page(3, "2. 자동차세\n자동차세는 매년 6월과 12월에 납부합니다.")), DOC);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).pageStart()).isEqualTo(1);
        assertThat(chunks.get(0).pageEnd()).isEqualTo(2);
        assertThat(chunks.get(0).content()).contains("건강보험에 자동으로 가입됩니다.");
        assertThat(chunks.get(1).pageStart()).isEqualTo(3);
        assertThat(chunks.get(1).pageEnd()).isEqualTo(3);
    }

    @Test
    void marksTableRowsAndKeepsThemTogether() {
        List<Chunk> chunks = chunker.split(List.of(page(1, """
                3. 수수료
                신청 종류별 수수료는 아래 표와 같습니다.
                구분 수수료 처리 기간
                외국인등록 30,000원 3주
                재발급 30,000원 2주
                체류지 변경 0원 1일
                수수료는 수입인지로 냅니다.
                """)), DOC);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().tableLike()).isTrue();
        assertThat(chunks.getFirst().content()).contains("외국인등록 30,000원 3주\n재발급 30,000원 2주\n체류지 변경 0원 1일");
    }

    @Test
    void plainProseIsNotTable() {
        List<Chunk> chunks = chunker.split(List.of(page(1, """
                음식물 쓰레기는 전용 봉투에 담아
                저녁 8시 이후 집 앞에 내놓으세요.
                재활용품은 종류별로 나누어 배출합니다.
                """)), DOC);

        assertThat(chunks).extracting(Chunk::tableLike).containsOnly(false);
    }

    @Test
    void taggedTableBecomesOwnChunkWithSectionTitle() {
        TableBlock table = new TableBlock(List.of("구분", "납부 방법", "비고"), List.of(
                row(11, "구분", "방문 납부", "납부 방법", "전국 은행·우체국·새마을금고·CD/ATM기 이용", "비고", "지로용지 지참"),
                row(11, "구분", "인터넷 납부", "납부 방법", "위택스(www.wetax.go.kr)", "비고", "공인인증서 필요")));
        List<Chunk> chunks = chunker.splitBlocks(List.of(
                new TextLine("1-4 지방세 납부", 11, true),
                new TextLine("■ 지방세 납부 방법", 11, true),
                new TextLine("지방세는 아래 방법으로 낼 수 있습니다.", 11, true),
                table,
                new TextLine("납부 기한이 지나면 가산금이 붙습니다.", 12, true)), DOC);

        assertThat(chunks).hasSize(3);
        assertThat(chunks).extracting(Chunk::title).containsOnly("1-4 지방세 납부 > ■ 지방세 납부 방법");
        assertThat(chunks).extracting(Chunk::structuredTable).containsExactly(false, true, false);
        assertThat(chunks.get(1).content()).isEqualTo("""
                1-4 지방세 납부 > ■ 지방세 납부 방법
                구분: 방문 납부 / 납부 방법: 전국 은행·우체국·새마을금고·CD/ATM기 이용 / 비고: 지로용지 지참
                구분: 인터넷 납부 / 납부 방법: 위택스(www.wetax.go.kr) / 비고: 공인인증서 필요""");
        assertThat(chunks.get(1).pageStart()).isEqualTo(11);
        assertThat(chunks.get(2).content()).doesNotContain("구분:");
        assertThat(chunks.get(2).pageStart()).isEqualTo(12);
    }

    @Test
    void shortLineRightBeforeTableIsHeading() {
        TableBlock table = new TableBlock(List.of("기관", "연락처"), List.of(
                row(14, "기관", "주미국한국대사관", "연락처", "☎+1-202-939-5600")));
        List<Chunk> chunks = chunker.splitBlocks(List.of(
                new TextLine("외국인등록은 90일 이내에 해야 합니다.", 14, true),
                new TextLine("주요 해외공관 연락처", 14, true),
                table), DOC);

        assertThat(chunks).extracting(Chunk::title).containsExactly(DOC, "주요 해외공관 연락처");
    }

    @Test
    void longTableSplitsAtRowBoundaries() {
        List<TableRow> rows = IntStream.rangeClosed(1, 40)
                .mapToObj(i -> row(i <= 20 ? 30 : 31, "행정복지센터", "동 " + i + "번", "주소", "시흥시 어딘가로 " + i + "번길 " + i,
                        "연락처", "☎(031)310-" + (4000 + i)))
                .toList();
        List<Chunk> chunks = chunker.splitBlocks(List.of(
                new TextLine("■ 행정복지센터", 30, true),
                new TableBlock(List.of("행정복지센터", "주소", "연락처"), rows)), DOC);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.content().length()).isLessThanOrEqualTo(DocumentChunker.MAX);
            assertThat(chunk.content()).startsWith("■ 행정복지센터\n");
            assertThat(chunk.structuredTable()).isTrue();
            chunk.content().lines().skip(1).forEach(line -> assertThat(line).matches("행정복지센터: 동 \\d+번 / 주소: .+ / 연락처: .+"));
        });
        assertThat(chunks.stream().mapToLong(chunk -> chunk.content().lines().count() - 1).sum()).isEqualTo(40);
        assertThat(chunks.getFirst().pageStart()).isEqualTo(30);
        assertThat(chunks.getLast().pageEnd()).isEqualTo(31);
    }

    // ---------------------------------------------------------------- 1. PLAIN 가짜 제목 (02·04·05 문서의 실제 줄)

    /** 02: "○ (개요) … 국내에서 최초로 / 근로를 제공한 날부터 … 발생한 / 근로소득에 대해 …" 는 한 문장 */
    @Test
    void wrappedSentenceIsNotHeadingInPlainPdf() {
        List<Chunk> chunks = chunker.splitBlocks(List.of(
                plain("2. 외국인 근로자에게 적용되는 특례규정", 6, 13.3f),
                plain(" 외국인 기술자 소득세 감면", 6, 13.3f),
                plain("○ (개요) 일정 요건을 갖춘 외국인 기술자는 국내에서 최초로", 6, 12.5f),
                plain("근로를 제공한 날부터 10년*이 되는 날이 속하는 달까지 발생한", 6, 12.5f),
                plain("근로소득에 대해 소득세 50%를 감면받을 수 있습니다.", 6, 12.5f),
                plain("* ’23.1.1. 당시 최초로 근로를 제공한 날부터 5년이 지나지 아니한 외국인", 6, 10.8f),
                plain("기술자에 대해서도 감면기간 10년 적용", 6, 10.8f)), DOC, Source.PLAIN);

        assertThat(chunks).extracting(Chunk::title)
                .containsOnly("2. 외국인 근로자에게 적용되는 특례규정 >  외국인 기술자 소득세 감면");
        assertThat(chunks.getFirst().content())
                .contains("국내에서 최초로 근로를 제공한 날부터", "아니한 외국인 기술자에 대해서도");
    }

    /**
     * 01 p7·p11: "PART 1." 번호 줄은 장 이름과 이어 새 장이 되고 (표지의 더 큰 글자 아래로 들지 않는다),
     * 따로 놓인 장-절 번호 "1-4" 는 앞 제목 줄과 합친다.
     */
    @Test
    void chapterNumberStartsNewChapterAndSectionNumberJoinsTitle() {
        List<Chunk> chunks = chunker.splitBlocks(List.of(
                tagged("시흥 처음 어서와~", 3, 34, true),
                tagged("책자에 나오는 정보는 2025년 기준입니다.", 3, 8, false),
                tagged("PART 1.", 7, 18, true),
                tagged("생활 정보", 7, 24, true),
                tagged("1-3 반려견 에티켓", 10, 10, true),
                tagged("· 문의 : 시흥시청 동물축산과 ☎(031)310-6945", 10, 8, false),
                tagged("지방세 납부", 11, 10, true),
                tagged("1-4", 11, 10, true),
                tagged("■ 재산세", 11, 9, true),
                tagged("· 납부기간 : 매년 7월, 9월 중", 11, 8, false)), DOC, Source.TAGGED);

        assertThat(chunks).extracting(Chunk::title).containsExactly(
                "시흥 처음 어서와~",
                "PART 1. 생활 정보 > 1-3 반려견 에티켓",
                "PART 1. 생활 정보 > 1-4 지방세 납부 > ■ 재산세");
    }

    /** 05: 쪽 오른쪽 끝까지 찬 줄은 다음 줄로 이어지는 문장 ("④ … 어느 하나에 해 / 당하는 …") */
    @Test
    void fullWidthLineIsNotHeadingInPlainPdf() {
        List<Chunk> chunks = chunker.splitBlocks(List.of(
                plain("기술연수(D-3)", 69, 20),
                plain("본 장은 기술연수 자격의 체류 관리 기준을 설명합니다.", 69, 15),
                new TextLine("④ 주소지관할 출입국․외국인관서의 장은 제1항 각 호의 어느 하나에 해", 69, false, 15, false, true),
                plain("당하는 업체에 대하여 다음과 같이 조치하여야 한다.", 69, 15)), DOC, Source.PLAIN);

        assertThat(chunks).extracting(Chunk::title).containsOnly("기술연수(D-3)");
        assertThat(chunks.getFirst().content()).contains("어느 하나에 해 당하는 업체");
    }

    /** PLAIN 에서는 번호·기호가 없으면 본문보다 크거나 굵어야 제목 */
    @Test
    void plainPdfNeedsMarkerOrEmphasisForHeading() {
        String body = "이사한 날부터 15일 이내에 새 주소지 행정복지센터에 신고합니다.";
        List<Chunk> same = chunker.splitBlocks(List.of(
                plain("외국인등록은 입국일부터 90일 이내에 해야 합니다.", 1, 12),
                plain("체류지 변경 신고", 1, 12),
                plain(body, 1, 12)), DOC, Source.PLAIN);
        List<Chunk> larger = chunker.splitBlocks(List.of(
                plain("외국인등록은 입국일부터 90일 이내에 해야 합니다.", 1, 12),
                plain("체류지 변경 신고", 1, 16),
                plain(body, 1, 12)), DOC, Source.PLAIN);

        assertThat(same).extracting(Chunk::title).containsOnly(DOC);
        assertThat(larger).extracting(Chunk::title).containsExactly(DOC, "체류지 변경 신고");
    }

    @Test
    void plausibleTitleRejectsSentencePiecesFormFieldsTocAndCitations() {
        assertThat(DocumentChunker.isPlausibleTitle("할 수 있음")).isFalse();
        assertThat(DocumentChunker.isPlausibleTitle("입국허가를 받아야 함")).isFalse();
        assertThat(DocumentChunker.isPlausibleTitle("근로를 제공한 날부터 10년*이 되는 날이 속하는 달까지 발생한")).isFalse();
        assertThat(DocumentChunker.isPlausibleTitle("② 벌금형 300만원 미만 : 형이 확정된 시점과")).isFalse();
        assertThat(DocumentChunker.isPlausibleTitle("Ⅲ  기타 참고사항 > 년/年       월/月       일/日")).isFalse();
        assertThat(DocumentChunker.isPlausibleTitle("년/年       월/月       일/日")).isFalse();
        assertThat(DocumentChunker.isPlausibleTitle("제13조에 따라 초청을 제한하는 업체")).isFalse();
        assertThat(DocumentChunker.isPlausibleTitle("1. 유학, 연수 (D-2, D-4) 09")).isFalse();
        assertThat(DocumentChunker.isPlausibleTitle("외국인 체류 안내 …… 12")).isFalse();
        assertThat(DocumentChunker.isPlausibleTitle("목  차")).isFalse();
        assertThat(DocumentChunker.isPlausibleTitle("< 목    차 >")).isFalse();
        assertThat(DocumentChunker.isPlausibleTitle(")-외국인근로자 고용관리-4대 사회보험]")).isFalse();
        assertThat(DocumentChunker.isPlausibleTitle("관서의 장에게29) 사증발급인정서 발급을 신청하여야 한다.30)")).isFalse();
        assertThat(DocumentChunker.isPlausibleTitle("법 무 부")).isFalse();
        assertThat(DocumentChunker.isPlausibleTitle("출입국․외국인정책본부")).isFalse();

        assertThat(DocumentChunker.isPlausibleTitle("제13조(연수생 관리 부실업체의 연수제한)")).isTrue();
        assertThat(DocumentChunker.isPlausibleTitle("13. 영주권자의 국내 출생자녀〔F-5-20〕")).isTrue();
        assertThat(DocumentChunker.isPlausibleTitle("1. 과태료 부과")).isTrue();
        assertThat(DocumentChunker.isPlausibleTitle("체류지 변경 신고")).isTrue();
        assertThat(DocumentChunker.isPlausibleTitle("PART 02")).isTrue();
        assertThat(DocumentChunker.isPlausibleTitle("주요 해외공관 연락처")).isTrue();
        // "…부" 로 끝나도 기관 서명이 아닌 제목 (01 p11 "1-4 지방세 납부")
        assertThat(DocumentChunker.isPlausibleTitle("지방세 납부")).isTrue();
    }

    /** 05 p473: 번호와 "〔F-5-20〕" 의 숫자 때문에 표의 행으로 보면 제목이 본문에 묻힌다 */
    @Test
    void numberedHeadingWithCodesIsNotTableRow() {
        assertThat(DocumentChunker.isTableRow("13. 영주권자의 국내 출생자녀〔F-5-20〕")).isFalse();
        assertThat(DocumentChunker.isTableRow("외국인등록 30,000원 3주")).isTrue();
    }

    // ---------------------------------------------------------------- 2. 제목 단계 (05)

    /**
     * 05: 본문 속 "제13조(…)" 가 고정 단계(제N조 > "1.")로 p70~267 내내 상위 제목이 되던 문제.
     * 처음 나온 순서로 단계를 정하면 "2." 가 나올 때 지워진다. 더 큰 글자의 장 제목("영 주(F-5)" 24pt)은
     * 더 작은 장 제목("방문동거(F-1)" 20pt) 아래에 들지 않는다.
     */
    @Test
    void headingLevelsFollowFirstAppearanceAndFontSize() {
        List<Chunk> chunks = chunker.splitBlocks(List.of(
                plain("외교(A-1)", 14, 20),
                plain("활동범위는 외교 활동을 하는 사람과 그 가족입니다.", 14, 12),
                plain("1. 체류자격 변경허가", 15, 12),
                plain("가. 제출서류", 15, 12),
                plain("신청서, 여권, 외국인등록증, 수수료를 제출합니다.", 15, 12),
                plain("제13조(연수생 관리 부실업체의 연수제한)", 15, 12),
                plain("주소지관할 출입국·외국인관서의 장은 연수생 초청을 제한할 수 있습니다.", 15, 12),
                plain("2. 체류기간 연장허가", 16, 12),
                plain("체류기간 만료 전에 연장허가를 받아야 합니다.", 16, 12),
                plain("방문동거(F-1)", 342, 20),
                plain("친척방문, 가족동거, 피부양 목적으로 체류하는 사람입니다.", 342, 12),
                plain("영 주(F-5)", 432, 24),
                plain("체류자격의 구분에 따른 활동의 제한을 받지 않습니다.", 432, 12),
                plain("13. 영주권자의 국내 출생자녀〔F-5-20〕", 473, 12),
                plain("가. 대상 (출입국관리법 시행령 별표 1의3 2호 후단)", 473, 12),
                plain("○ 대한민국에서 출생한 것을 이유로 체류자격 부여 신청을 한 사람", 473, 12)), DOC, Source.PLAIN);

        assertThat(chunks).extracting(Chunk::title).contains(
                "외교(A-1) > 1. 체류자격 변경허가 > 가. 제출서류",
                "외교(A-1) > 2. 체류기간 연장허가",
                "방문동거(F-1)",
                "영 주(F-5)",
                "영 주(F-5) > 13. 영주권자의 국내 출생자녀〔F-5-20〕 > 가. 대상 (출입국관리법 시행령 별표 1의3 2호 후단)");
        assertThat(chunks).extracting(Chunk::title).noneMatch(title -> title.startsWith("제13조")
                || title.contains("제13조") && title.contains("2. 체류기간"));
    }

    /** 목차 쪽의 "목 차" 와 점선 목차 줄은 제목이 아니어서 단계를 망치지 않는다 */
    @Test
    void tableOfContentsDoesNotBecomeTopTitle() {
        List<Chunk> chunks = chunker.splitBlocks(List.of(
                plain("목  차", 2, 16),
                plain("Ⅰ. 외국인등록 ………………………… 3", 2, 12),
                plain("Ⅰ. 외국인등록", 3, 16),
                plain("입국일부터 90일 이내에 출입국·외국인청에 신청해야 합니다.", 3, 12),
                plain("Ⅱ. 체류기간 연장", 4, 16),
                plain("체류기간이 끝나기 전에 연장허가를 받아야 합니다.", 4, 12)), DOC, Source.PLAIN);

        assertThat(chunks).extracting(Chunk::title).doesNotContain("목  차")
                .contains("Ⅰ. 외국인등록", "Ⅱ. 체류기간 연장")
                .allSatisfy(title -> assertThat(title).doesNotContain("목  차", "……"));
    }

    // ---------------------------------------------------------------- 4. 태그 PDF 의 떨어진 기호·괄호 줄 (07)

    @Test
    void joinsSymbolAndBracketFragmentsInTaggedPdf() {
        List<Chunk> chunks = chunker.splitBlocks(List.of(
                new TextLine("사회보험 가입", 13, true),
                new TextLine("국민연금은 소득활동을 할 때 보험료를 납부하는 제도입니다.", 13, true),
                new TextLine("[출처: workinkorea 홈페이지(", 13, true),
                new TextLine("https://www.workinkorea.org", 13, true),
                new TextLine(")-외국인근로자 고용관리-4대 사회보험]", 13, true),
                new TextLine("※", 13, true),
                new TextLine("외국인근로자 4대 보험 관련 상담", 13, true),
                new TextLine("• 상담전화: ☎1577-0071", 13, true)), DOC, Source.TAGGED);

        assertThat(chunks).extracting(Chunk::title).containsOnly("사회보험 가입");
        assertThat(chunks.getFirst().content()).contains(
                "[출처: workinkorea 홈페이지(https://www.workinkorea.org)-외국인근로자 고용관리-4대 사회보험]",
                "※ 외국인근로자 4대 보험 관련 상담");
        assertThat(chunks.getFirst().content().lines()).noneMatch(line -> line.strip().matches("[※()]"));
    }

    // ---------------------------------------------------------------- 5. 짧은 조각 합치기

    /** 같은 절의 짧은 끝 조각은 앞 조각과 합치고, 앞 조각 끝을 겹친 부분은 한 번만 남긴다 */
    @Test
    void mergesShortChunkIntoSameSectionWithoutDuplicatingOverlap() {
        String longParagraph = IntStream.rangeClosed(1, 15)
                .mapToObj(i -> "생활 안내 " + i + "번 문장은 외국인 주민이 알 내용입니다.")
                .collect(Collectors.joining(" "));
        String shortParagraph = "문의는 1345로 하면 됩니다.";
        List<Chunk> chunks = chunker.split(List.of(page(1, "1. 생활 안내\n" + longParagraph + "\n" + shortParagraph)), DOC);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().body()).isEqualTo(longParagraph + "\n" + shortParagraph);
    }

    /** 표지·간지처럼 문장으로 끝나지 않는 아주 짧은 조각은 다음 절 첫 조각에 붙인다 (쪽 범위도 합친다) */
    @Test
    void attachesTinyCoverChunkToNextSection() {
        List<Chunk> chunks = chunker.splitBlocks(List.of(
                new TextLine("www.hikorea.go.kr", 1, true),
                new TextLine("1. 외국인등록", 2, true),
                new TextLine("외국인등록은 입국일부터 90일 이내에 출입국·외국인청에 신청해야 합니다.", 2, true)), DOC, Source.TAGGED);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().title()).isEqualTo("1. 외국인등록");
        assertThat(chunks.getFirst().body()).startsWith("www.hikorea.go.kr\n외국인등록은");
        assertThat(chunks.getFirst().pageStart()).isEqualTo(1);
        assertThat(chunks.getFirst().pageEnd()).isEqualTo(2);
    }

    @Test
    void doesNotMergeTableChunks() {
        TableBlock table = new TableBlock(List.of("구분", "수수료"), List.of(
                row(5, "구분", "외국인등록증 발급", "수수료", "3만원")));
        List<Chunk> chunks = chunker.splitBlocks(List.of(
                new TextLine("■ 수수료", 5, true),
                new TextLine("수수료는 아래와 같습니다.", 5, true),
                table), DOC, Source.TAGGED);

        assertThat(chunks).extracting(Chunk::structuredTable).containsExactly(false, true);
    }

    // ---------------------------------------------------------------- 6. 제목 경로·형제 절 묶기 (CB-01 평가: 07, 08, 05)

    /**
     * 07 p13~14: 태그 PDF 의 번호 없는 제목도 글자 크기·굵기로 단계가 갈린다 (15pt 굵게 &gt; 13pt 굵게 &gt; 13pt).
     * 작은 형제 절("보험금 지급사유", "위반 시 제재")은 부모 제목 아래 한 조각으로 묶고, 하위 제목은 본문 줄로 남는다.
     * 표 조각의 제목에는 상위 제목이 붙어 "귀국비용보험" 으로도 찾을 수 있다.
     */
    @Test
    void taggedHeadingsGetParentPathAndSmallSiblingsAreMerged() {
        TableBlock fees = new TableBlock(List.of("구 분", "납부금액"), List.of(
                row(13, "구 분", "제1군", "납부금액", "40만원"),
                row(13, "구 분", "제2군", "납부금액", "50만원")));
        List<Chunk> chunks = chunker.splitBlocks(List.of(
                tagged("외국인근로자 취업", 13, 20, true),
                tagged("3.1.3. 외국인근로자 전용보험", 13, 15, true),
                tagged("귀국비용보험·신탁 가입", 13, 13, true),
                tagged("귀국비용보험·신탁은 외국인근로자가 귀국할 때 필요한 비용에 충당하기 위해 가입하는 보험입니다.", 13, 13, false),
                tagged("납부금액", 13, 13, false),
                tagged("귀국비용보험등의 납부금액은 다음과 같습니다.", 13, 13, false),
                fees,
                tagged("보험금 지급사유", 14, 13, false),
                tagged("체류기간이 만료되어 출국하려는 경우 일시금의 지급을 신청할 수 있습니다.", 14, 13, false),
                tagged("위반 시 제재", 14, 13, false),
                tagged("귀국비용보험등에 가입하지 않으면 500만원 이하의 과태료가 부과됩니다.", 14, 13, false),
                tagged("상해보험 가입", 14, 13, true),
                tagged("상해보험은 업무상 재해 이외의 질병·사망 등에 대비해서 가입하는 보험입니다.", 14, 13, false),
                tagged("위반 시 제재", 14, 13, false),
                tagged("상해보험에 가입하지 않으면 500만원 이하의 벌금이 부과됩니다.", 14, 13, false)), DOC, Source.TAGGED);

        assertThat(chunks).filteredOn(Chunk::structuredTable).singleElement()
                .extracting(Chunk::title)
                .isEqualTo("3.1.3. 외국인근로자 전용보험 > 귀국비용보험·신탁 가입 > 납부금액");
        Chunk merged = chunks.stream().filter(chunk -> chunk.content().contains("과태료")).findFirst().orElseThrow();
        assertThat(merged.title()).isEqualTo("외국인근로자 취업 > 3.1.3. 외국인근로자 전용보험 > 귀국비용보험·신탁 가입");
        assertThat(merged.body().lines()).contains("보험금 지급사유", "위반 시 제재");
        assertThat(merged.content()).doesNotContain("상해보험");
        // 같은 이름의 하위 절도 어느 보험 이야기인지 제목에 남는다
        Chunk injury = chunks.stream().filter(chunk -> chunk.content().contains("벌금")).findFirst().orElseThrow();
        assertThat(injury.title()).contains("상해보험 가입");
    }

    /**
     * 02 p13~14: 앞 조각에서 이어지는 절의 꼬리를 형제 절과 묶을 때는 "… (이어서)" 로 표시한다.
     * 꼬리 내용 위에 원래 제목이 그대로 오면 그 절의 첫 내용으로 읽힌다.
     */
    @Test
    void continuedTailIsMarkedWhenMergedWithSibling() {
        String reductions = IntStream.rangeClosed(1, 34)
                .mapToObj(i -> "감면 " + i + "번 문장은 외국인 근로자의 세액 감면 요건을 설명합니다.")
                .collect(Collectors.joining(" "));
        List<Chunk> chunks = chunker.splitBlocks(List.of(
                tagged("1장 연말정산", 11, 20, true),
                tagged("1-1 세액계산", 11, 15, true),
                tagged("① 정부간 협약 감면", 11, 13, true),
                tagged(reductions, 11, 10, false),
                tagged("① 근로소득세액공제", 11, 13, true),
                tagged("산출세액의 55%를 공제합니다.", 11, 10, false)), DOC, Source.TAGGED);

        assertThat(chunks.getFirst().title()).isEqualTo("1장 연말정산 > 1-1 세액계산 > ① 정부간 협약 감면");
        Chunk merged = chunks.getLast();
        assertThat(merged.title()).isEqualTo("1장 연말정산 > 1-1 세액계산");
        assertThat(merged.body().lines().toList())
                .startsWith("① 정부간 협약 감면" + DocumentChunker.CONTINUED)
                .contains("① 근로소득세액공제");
    }

    /**
     * 03 "비자 내비게이터", 05 "외국인체류 안 내 매 뉴 얼": 표지 쪽이 아니어도, 내용 있는 절의 절반 넘게
     * 경로 맨 앞에 붙는 번호 없는 1단계 제목은 표지로 보고 뺀다. 번호 있는 장("Ⅱ. …")은 절반을 넘어도 장이다.
     */
    @Test
    void majorityTopTitleIsTreatedAsCover() {
        List<ContentBlock> manual = new java.util.ArrayList<>(List.of(
                tagged("외국인체류 안내 매뉴얼", 5, 24, true),
                tagged("이 매뉴얼은 체류자격별 안내입니다.", 5, 10, false)));
        for (int i = 1; i <= DocumentChunker.MAJORITY_MIN_SECTIONS; i++) {
            String name = "체류 유형 " + (char) ('가' + i * 28);
            manual.add(tagged(name, 10 + i, 20, true));
            manual.add(tagged(name + " 의 활동 범위를 설명합니다.", 10 + i, 10, false));
        }
        manual.add(tagged("1. 체류기간 연장", 40, 12, true));
        manual.add(tagged("만료 전에 신청합니다.", 40, 10, false));
        List<Chunk> cover = chunker.splitBlocks(manual, DOC, Source.TAGGED);
        List<Chunk> chapter = chunker.split(List.of(page(4, """
                Ⅱ. 외국인 근로자의 연말정산
                1. 거주자와 비거주자
                국내에 주소를 둔 개인은 거주자입니다.
                2. 특례
                외국인 기술자는 감면을 받습니다.
                Ⅲ. 계산사례
                계산 사례를 설명합니다.
                """)), DOC);

        assertThat(cover.getFirst().title()).isEqualTo("외국인체류 안내 매뉴얼");
        assertThat(cover.subList(1, cover.size())).extracting(Chunk::title)
                .noneMatch(title -> title.contains("매뉴얼"))
                .contains("체류 유형 " + (char) ('가' + 28), "체류 유형 " + (char) ('가' + 20 * 28) + " > 1. 체류기간 연장");
        assertThat(chapter).extracting(Chunk::title).contains(
                "Ⅱ. 외국인 근로자의 연말정산 > 1. 거주자와 비거주자", "Ⅱ. 외국인 근로자의 연말정산 > 2. 특례");
    }

    /** 부모가 최상위 제목(장)뿐인 형제 절, 합치면 2쪽을 넘는 형제 절은 묶지 않는다 */
    @Test
    void doesNotMergeSiblingsUnderTopLevelOnlyOrAcrossTooManyPages() {
        List<Chunk> topLevel = chunker.split(List.of(page(1, """
                2. 외국인등록
                가. 신청 기한
                입국일부터 90일 이내에 신청해야 합니다.
                나. 준비물
                여권, 사진 1장, 수수료 3만 원이 필요합니다.
                """)), DOC);
        List<Chunk> pages = chunker.splitBlocks(List.of(
                tagged("1장 생활", 11, 20, true),
                tagged("1-1 보험", 11, 15, true),
                tagged("건강보험", 11, 13, true),
                tagged("6개월 이상 체류하면 가입합니다.", 11, 13, false),
                tagged("고용보험", 12, 13, true),
                tagged("근로자는 가입합니다.", 12, 13, false),
                tagged("산재보험", 13, 13, true),
                tagged("사업장은 가입합니다.", 13, 13, false)), DOC, Source.TAGGED);

        assertThat(topLevel).extracting(Chunk::title)
                .containsExactly("2. 외국인등록 > 가. 신청 기한", "2. 외국인등록 > 나. 준비물");
        assertThat(pages).extracting(Chunk::title)
                .containsExactly("1장 생활 > 1-1 보험", "1장 생활 > 1-1 보험 > 산재보험");
        assertThat(pages).allSatisfy(chunk ->
                assertThat(chunk.pageEnd() - chunk.pageStart() + 1).isLessThanOrEqualTo(DocumentChunker.MERGE_MAX_PAGES));
    }

    /**
     * 08 p5~7: 별표 쪽은 쪽 전체 글자가 본문보다 커서(83 → 100) 표 칸("결혼이민(F-6)")이 강조된 제목으로 보였다.
     * 쪽 기준 크기로 보면 표 칸은 제목이 아니고, "[별표 2] …" 머리 줄과 그 아래 제목 줄이 한 제목이 된다.
     */
    @Test
    void appendixTitleBecomesHeadingAndTableCellsOnLargerPageDoNot() {
        String article = "제4조(자격 취득) 국내체류 외국인등은 입국일부터 6개월이 지난 날에 지역가입자의 자격을 얻는다. ";
        List<ContentBlock> blocks = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            blocks.add(plain(article + "다만 공단이 정하는 경우에는 그러하지 아니하다.", 4, 83));
        }
        blocks.addAll(List.of(
                plain("[별표 1] <신설 2018.12.18.>", 5, 100),
                plain("재외국민 및 외국인 지역가입자의 제출서류(제4조제4항 관련)", 5, 117),
                plain("구분 제출서류", 5, 100),
                plain("○ 가족관계나 혼인·이혼사실을 확인할 수 있는 서류(세대원인 경우에 한정한다)", 5, 100),
                plain("○ 소득 및 재산의 정도를 확인할 수 있는 다음의 서류", 5, 100),
                plain("○ 외국인등록사실증명원", 5, 100),
                plain("영주(F-5)", 5, 100),
                plain("○ 「장애인복지법」에 따라 등록된 장애인임을 증명할 수", 5, 100),
                plain("결혼이민(F-6)", 5, 100),
                plain("있는 서류 등(보험료 경감을 신청하는 경우만 해당한다)", 5, 100),
                plain("그 밖의 체류자격 ○ 외국인등록사실증명원", 5, 100),
                plain("[별표 2] <개정 2019.7.11., 개정 2021.2.26., 개정 2021.10.14>", 6, 100),
                plain("지역보험료 산정 기준(제6조제1항 관련)", 6, 117),
                plain("1. 재외국민 및 외국인인 지역가입자의 보험료는 세대 단위로 내국인인 지역가입자와 동일한 기준에 따라 산정한다.", 6, 100),
                plain("2) 체류자격이 종교(D-6)인 경우: 100분의 30", 6, 100),
                plain("4) 체류자격이 기타(G-1)인 경우: 100분의 30", 6, 100)));

        List<Chunk> chunks = chunker.splitBlocks(blocks, DOC, Source.PLAIN);

        assertThat(chunks).extracting(Chunk::title).contains(
                "[별표 1] 재외국민 및 외국인 지역가입자의 제출서류(제4조제4항 관련)",
                "[별표 2] 지역보험료 산정 기준(제6조제1항 관련)");
        assertThat(chunks).extracting(Chunk::title).noneMatch(title -> title.contains("결혼이민(F-6)")
                || title.contains("영주(F-5)") || title.contains("<개정"));
        Chunk religion = chunks.stream().filter(chunk -> chunk.content().contains("종교(D-6)")).findFirst().orElseThrow();
        assertThat(religion.title()).isEqualTo("[별표 2] 지역보험료 산정 기준(제6조제1항 관련)");
    }

    /** "[별표1의2] 의 24. 거주(F-2) 타목의 …" 처럼 대괄호 뒤에 본문이 이어지면 별표 제목이 아니다 */
    @Test
    void appendixCitationInBodyIsNotHeading() {
        List<Chunk> chunks = chunker.splitBlocks(List.of(
                plain("영 주(F-5)", 479, 24),
                plain("- 신청일 이전 2년부터 심사 결정일까지 「출입국관리법 시행령」", 479, 12),
                plain("[별표1의2] 의 24. 거주(F-2) 타목의 체류자격으로 체류할 것", 479, 12),
                plain("* 신청일 전 이혼 후 다시 재결합한 경우 혼인관계 계속성 불인정", 479, 11)), DOC, Source.PLAIN);

        assertThat(chunks).extracting(Chunk::title).containsOnly("영 주(F-5)");
    }

    /** 문서 첫 제목이 앞 3쪽 안에서 가장 큰 글자면 표지 제목: 아래 제목의 경로에서 뺀다 (같은 글자의 제목이 다시 나와도) */
    @Test
    void coverTitleIsLeftOutOfLowerTitles() {
        List<Chunk> chunks = chunker.splitBlocks(List.of(
                tagged("비자 내비게이터", 1, 30, true),
                tagged("법무부 출입국·외국인정책본부가 만든 안내서입니다.", 1, 10, false),
                tagged("비자 내비게이터", 2, 30, true),
                tagged("01 비자의 개요", 3, 16, true),
                tagged("비자는 외국인이 입국할 때 필요한 허가입니다.", 3, 10, false)), DOC, Source.TAGGED);

        assertThat(chunks).extracting(Chunk::title).containsExactly("비자 내비게이터", "01 비자의 개요");
    }

    /** "3.1.3." 처럼 점이 여러 개인 번호는 제목 번호이고, 숫자 개수로 단계가 갈린다 */
    @Test
    void decimalSectionNumbersAreHeadingsWithLevels() {
        List<Chunk> chunks = chunker.split(List.of(page(12, """
                3.1. 근로
                근로 조건을 설명합니다.
                3.1.2. 4대 보험
                외국인근로자도 4대 보험에 가입합니다.
                """)), DOC);

        assertThat(chunks).extracting(Chunk::title).containsExactly("3.1. 근로", "3.1. 근로 > 3.1.2. 4대 보험");
    }

    /** 조각이 "※ …" 처럼 다음 줄로 이어지는 짧은 머리 문장으로 끝나면 그 문장은 다음 조각으로 넘긴다 */
    @Test
    void leadInLineMovesToNextChunk() {
        String first = IntStream.rangeClosed(1, 20)
                .mapToObj(i -> "고용보험 " + i + "번 문장은 가입 대상을 설명합니다.")
                .collect(Collectors.joining(" "));
        String second = IntStream.rangeClosed(1, 8)
                .mapToObj(i -> "비교표 " + i + "번 문장은 보험별 가입 방법입니다.")
                .collect(Collectors.joining(" "));
        List<Chunk> chunks = chunker.splitBlocks(List.of(
                new TextLine("1. 보험", 13, true),
                new TextLine(first, 13, true),
                new TextLine("※ 외국인근로자에게 적용되는", 13, true),
                new TextLine(second, 13, true)), DOC, Source.TAGGED);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).body()).doesNotContain("※");
        assertThat(chunks.get(1).body()).startsWith("※ 외국인근로자에게 적용되는\n비교표 1번");
    }

    /**
     * 05 p480: 체류관리 표의 칸 이름이 값 줄 사이에 끼어 있던 것을 "칸 이름: 값" 행으로 바꾸고,
     * 앞 하위 자격 제목이 아니라 자격 제목 바로 아래 "체류관리" 로 둔다. 표가 끝나면 다음 상자 제목("코로나19 …")은 자격 아래로.
     */
    @Test
    void stayManagementTableRowsGetLabelsAndQualificationTitle() {
        List<Chunk> chunks = chunker.splitBlocks(List.of(
                plain("영 주(F-5)", 478, 24),
                plain("17. 외국인투자기업의 연구개발 인력〔F-5-26〕", 478, 12),
                plain("3) 영주(F-5-28)자격을 가진 사람의 미성년 자녀(F-5-29)", 479, 12),
                plain("◦ 신청인의 부 또는 모(영주자격을 가진 사람) 요건", 479, 12),
                plain("- 신청인의 영주 신청일 기준 최소 2년 이전에 영주(F-5-28) 자격을 취득하여 계속 유지할 것", 480, 12),
                plain("다. 심사서류", 480, 12),
                plain("◦ 가족관계 등 입증 서류(국내 출생인 경우 국내 출생증명서)", 480, 12),
                plain("체류기간", 480, 12),
                plain("해당사항 없음", 480, 12),
                plain("연장허가", 480, 12),
                plain("출국한 날부터 2년 이내에 재입국하고자 하는 자에 대하여는 재입국허가", 480, 12),
                plain("면제", 480, 12),
                plain("재입국허가 출국한 날부터 2년을 초과하고자 하는 경우 기간만료 전에 재외공관", 480, 12),
                plain("에서 재입국허가 연장 필요(허가기간 3월 이내)", 480, 12),
                plain("- 공관장의 허가범위 : 출국한날부터 2년이 만료되는 시점부터 3월 이내", 480, 12),
                plain("외국인등록", 480, 12),
                plain("신청서(별지 34호 서식), 표준규격사진 1매, 수수료", 480, 12),
                plain("체류지 입증서류", 480, 12),
                new TextLine("코로나19 관련 영주(F-5)자격 상실자 구제 기준 알림", 481, false, 18, false, true),
                plain("□ 구제 대상", 481, 15),
                plain("코로나 19로 인해 재입국허가(면제)기간이 도과되어 영주자격이 상실된 자", 481, 14)), DOC, Source.PLAIN);

        Chunk table = chunks.stream().filter(chunk -> chunk.content().contains("공관장")).findFirst().orElseThrow();
        assertThat(table.title()).isEqualTo("영 주(F-5) > 체류관리");
        assertThat(table.body().lines()).containsExactly(
                "체류기간 연장허가: 해당사항 없음",
                "재입국허가: 출국한 날부터 2년 이내에 재입국하고자 하는 자에 대하여는 재입국허가 면제 "
                        + "출국한 날부터 2년을 초과하고자 하는 경우 기간만료 전에 재외공관 에서 재입국허가 연장 필요(허가기간 3월 이내) "
                        + "- 공관장의 허가범위 : 출국한날부터 2년이 만료되는 시점부터 3월 이내",
                "외국인등록: 신청서(별지 34호 서식), 표준규격사진 1매, 수수료 체류지 입증서류");
        assertThat(chunks).extracting(Chunk::title)
                .anyMatch(title -> title.startsWith("영 주(F-5) > 코로나19 관련 영주(F-5)자격 상실자 구제 기준 알림"))
                .noneMatch(title -> title.contains("체류관리 >"));
    }

    /** 자격 제목이 없는 곳(앞쪽 수수료 표 등)의 칸 이름은 행으로만 바꾸고 "체류관리" 제목은 넣지 않는다 */
    @Test
    void stayTableWithoutQualificationGetsNoSyntheticTitle() {
        List<Chunk> chunks = chunker.splitBlocks(List.of(
                plain("체류자격 부여", 5, 12),
                plain("8만원", 5, 12),
                plain("재입국허가", 5, 12),
                plain("3만원", 5, 12)), DOC, Source.PLAIN);

        assertThat(chunks).extracting(Chunk::title).containsOnly(DOC);
    }

    /** 05 p713~718: 묶음 문서 표제와 지역이 깊은 절의 제목에도 남는다. 부제는 제목 자리를 차지하지 않는다 */
    @Test
    void bundledDocumentTitleAndRegionStayInTitle() {
        List<Chunk> chunks = bundled(
                plain("광역형 비자 시범사업", 713, 27),
                plain("사증발급 및 체류관리지침 제정(안)", 713, 27),
                plain("Ⅰ 시범사업 개요", 716, 20),
                plain("1. 목적", 716, 17),
                plain(filler("시범사업 목적"), 716, 12),
                plain("Ⅱ 지자체별 요건 및 심사기준 - 유학", 718, 20),
                plain("1. 서울특별시", 718, 16.7f),
                plain("□ 시간제 취업 허용시간 확대", 718, 15),
                plain("3) 강화", 718, 14),
                plain("한국어능력 기준을 충족하지 못하면 주중 10시간 이내로 허용합니다.", 718, 12));

        assertThat(chunkWith(chunks, "주중 10시간 이내").title())
                .isEqualTo("광역형 비자 시범사업 > Ⅱ 지자체별 요건 및 심사기준 - 유학 > 1. 서울특별시 > 3) 강화");
        // 제목에서 빠진 중간 제목은 본문에 남는다
        assertThat(chunkWith(chunks, "주중 10시간 이내").body()).contains("□ 시간제 취업 허용시간 확대");
        assertThat(chunks).extracting(Chunk::title).noneMatch(title -> title.contains("제정(안)"));
        assertThat(chunks).extracting(Chunk::content).anyMatch(content -> content.contains("제정(안)"));
    }

    /** 05 p726, p745: 앞 내용이 문장으로 끝나지 않아도 더 큰 "5." 와 "Ⅲ" 은 제목. 지역이 다른 짧은 절은 합치지 않는다 */
    @Test
    void largerNumberedHeadingsStartRegionAndSectionAfterUnfinishedLine() {
        List<Chunk> chunks = bundled(
                plain("광역형 비자 시범사업", 713, 27),
                plain("Ⅱ 지자체별 요건 및 심사기준 - 유학", 718, 20),
                plain("4. 광주광역시", 725, 16.7f),
                plain("○ 광주 대상은 인공지능 학과이고 제출서류는 기존 지침", 725, 12),
                plain("사증발급 및 체류관리 지침 적용", 725, 12),
                plain("5. 강원특별자치도", 725, 16.7f),
                plain("○ 강원 대상은 바이오헬스 학과입니다.", 725, 12),
                plain("□ 쿼터 : 250명", 725, 15),
                plain("Ⅲ 지자체별 요건 및 특례 - 특정활동", 725, 20),
                plain("3. 경기도", 725, 16.7f),
                plain("○ 경기 대상은 산업기술인력과 요양보호사입니다.", 725, 12));

        Chunk gwangju = chunkWith(chunks, "광주 대상");
        assertThat(gwangju.title()).contains("4. 광주광역시");
        assertThat(gwangju.content()).doesNotContain("강원 대상");
        assertThat(chunkWith(chunks, "강원 대상").title()).contains("5. 강원특별자치도");
        assertThat(chunkWith(chunks, "경기 대상").title())
                .isEqualTo("광역형 비자 시범사업 > Ⅲ 지자체별 요건 및 특례 - 특정활동 > 3. 경기도");
    }

    /** 05 p592~611: 본문과 같은 크기여도 묶음 문서 안의 로마 숫자는 절이고, 앞 절의 "동반가족"이 남지 않는다 */
    @Test
    void romanSectionInBundledDocumentClearsPreviousSection() {
        List<Chunk> chunks = bundled(
                plain("지역특화형비자 체류제도 주요내용 알림", 590, 18),
                plain("Ⅰ. 지역특화형비자 사업 개요", 590, 15),
                plain("󰊴 동반가족", 590, 15),
                plain(filler("동반가족 요건"), 590, 12),
                plain("❍ 외국인등록증은 우편으로 받을 수 없고", 590, 12),
                new TextLine("Ⅲ. 지역특화 숙련기능인력(E-7-4R) 인구감소지역 및 인구감소관심지역", 590, false, 15, false, true),
                plain("󰊱 체류자격 변경", 590, 15),
                plain("숙련기능인력은 점수제 기준을 충족해야 합니다.", 590, 12));

        assertThat(chunkWith(chunks, "점수제 기준을").title())
                .startsWith("지역특화형비자 체류제도 주요내용 알림 > Ⅲ. 지역특화 숙련기능인력(E-7-4R)")
                .doesNotContain("동반가족");
    }

    /** 05 p764~765: 붙임은 묶음 문서 바로 아래. 앞 서식의 수신처("… 귀하")가 상위 제목이 되지 않는다 */
    @Test
    void attachmentInBundledDocumentStartsBelowItsTitle() {
        List<Chunk> chunks = bundled(
                plain("광역형 비자 시범사업", 713, 27),
                plain("충청남도지사 귀하", 764, 20),
                plain("참여 확인서를 제출합니다.", 764, 12),
                plain("붙임 8 특정활동 광역형 비자 적용 대상 직종", 765, 16),
                plain("조선용접공을 포함한 직종입니다.", 765, 12));

        assertThat(chunkWith(chunks, "조선용접공").title())
                .isEqualTo("광역형 비자 시범사업 > 붙임 8 특정활동 광역형 비자 적용 대상 직종");
    }

    /** 05 p777~782: 상자 번호가 "2." 아래인 지침에서는 "2." 가 제목에 남고, 다음 "4." 에서 상자가 빠진다 */
    @Test
    void boxNumberFollowsLevelUsedInThatBundledDocument() {
        List<Chunk> chunks = bundled(
                plain("K-STAR 비자트랙 제도 안내 매뉴얼", 775, 20),
                plain("Ⅱ. K-STAR 거주(F-2-7S)", 776, 15),
                plain("2. 자격요건", 777, 15),
                plain("󰊱 품행단정", 777, 15),
                plain(filler("품행단정 요건"), 777, 12),
                plain("4. 제출서류", 778, 15),
                plain("여권과 통합신청서를 제출합니다.", 778, 12));

        assertThat(chunkWith(chunks, "품행단정 요건 1번").title()).contains("2. 자격요건", "󰊱 품행단정");
        assertThat(chunkWith(chunks, "여권과 통합신청서").title())
                .isEqualTo("K-STAR 비자트랙 제도 안내 매뉴얼 > Ⅱ. K-STAR 거주(F-2-7S) > 4. 제출서류");
    }

    /** 05 p756: 내용 없이 바로 밀려난 제목도 글자가 남는다 */
    @Test
    void emptyHeadingInBundledDocumentKeepsText() {
        List<Chunk> chunks = bundled(
                plain("광역형 비자 시범사업", 713, 27),
                plain("Ⅲ 지자체별 요건 및 특례", 745, 20),
                plain("경남 특례를 설명합니다.", 755, 12),
                plain("붙임 문서", 756, 39),
                plain("붙임 1 지자체 추천서", 757, 16),
                plain("추천 대상을 적습니다.", 757, 12));

        assertThat(chunks).extracting(Chunk::content).anyMatch(content -> content.contains("붙임 문서"));
    }

    /** 05 p525~526, p672~673: 짧은 꼬리와 표제 앞 부제가 문서 경계를 넘지 않는다 */
    @Test
    void chunksDoNotCrossBundledDocuments() {
        List<Chunk> chunks = bundled(
                plain("국내 성장 기반 외국인 청소년 체류제도 알림", 659, 19),
                plain("Ⅳ 참고사항", 672, 20),
                plain("구직 계획 작성", 672, 12),
                plain("- 첨단산업 분야 최우수인재 -", 673, 20),
                plain("톱티어(Top-Tier) 사증발급 및 체류관리 지침", 673, 21),
                plain("톱티어 제도의 목적을 설명합니다.", 673, 12));

        Chunk previous = chunkWith(chunks, "구직 계획 작성");
        assertThat(previous.title()).startsWith("국내 성장 기반 외국인 청소년 체류제도 알림");
        assertThat(previous.pageEnd()).isEqualTo(672);
        assertThat(chunkWith(chunks, "첨단산업 분야").title()).startsWith("톱티어(Top-Tier)");
    }

    /** 05 p481: 자격 장 안의 상자 제목("… 알림")은 묶음 문서 표제가 아니어서 다음 자격 장을 덮지 않는다 */
    @Test
    void noticeBoxInsideQualificationChapterIsNotBundledDocument() {
        List<Chunk> chunks = chunker.splitBlocks(List.of(
                plain("영 주(F-5)", 478, 24),
                plain(filler("영주 요건"), 478, 12),
                plain("코로나19 관련 영주(F-5)자격 상실자 구제 기준 알림", 481, 18),
                plain("재입국허가 기간이 지난 사람을 구제합니다.", 481, 12),
                plain("결혼이민(F-6)", 482, 20),
                plain("국민의 배우자에게 부여합니다.", 482, 12)), DOC, Source.PLAIN);

        assertThat(chunkWith(chunks, "국민의 배우자").title()).isEqualTo("결혼이민(F-6)");
    }

    /** 05 p36~40: 두 줄로 나뉜 칸 이름 "시간제취업 / 활동 허가" 가 제목이 되고, 다음 칸에서 끝난다 */
    @Test
    void partTimeWorkRowDoesNotInheritSpouseHeading() {
        List<Chunk> chunks = chunker.splitBlocks(List.of(
                plain("유 학(D-2)", 35, 20),
                plain(filler("유학 자격"), 35, 12),
                plain("3. 유학생(D-2) 배우자(F-3) 체류자격외 활동 허가", 36, 12),
                plain("❍ 동반(F-3)자격 지침에 따름", 36, 12),
                plain("시간제취업 가. 기본원칙", 37, 12),
                plain("활동 허가 ❍ 학생이 통상적으로 하는 활동에 한정합니다.", 37, 12),
                plain("다. 허용범위", 37, 12),
                plain("한국어 기준을 충족한 학사는 주중 25시간까지 허용합니다.", 37, 12),
                plain("시간제취업 라. 허가 기준 등", 39, 12),
                plain("활동 허가 ❍ 성적이 낮으면 제한합니다.", 39, 12),
                plain("근무처의 ❍ 해당사항 없음", 40, 12),
                plain("변경․추가 ※ 소속 변경은 신고로 처리합니다.", 40, 12)), DOC, Source.PLAIN);

        Chunk hours = chunkWith(chunks, "주중 25시간");
        assertThat(hours.title()).startsWith("유 학(D-2) > 시간제취업 활동 허가").doesNotContain("배우자");
        assertThat(chunkWith(chunks, "성적이 낮으면").title()).startsWith("유 학(D-2) > 시간제취업 활동 허가");
        // 짧은 배우자 규정 한 줄은 시간제취업 조각으로 넘어가지 않는다
        assertThat(chunkWith(chunks, "동반(F-3)자격").title()).contains("배우자(F-3)");
        assertThat(chunkWith(chunks, "소속 변경은").title()).isEqualTo("유 학(D-2)");
    }

    /** 자격 제목이 없으면 칸 이름을 떼지 않는다 (제목으로도 못 넣어 글자가 사라진다) */
    @Test
    void partTimeWorkLabelWithoutQualificationKeepsText() {
        List<Chunk> chunks = chunker.splitBlocks(List.of(
                plain("시간제취업 가. 기본원칙", 5, 12),
                plain("활동 허가 ❍ 학생이 통상적으로 하는 활동에 한정합니다.", 5, 12)), DOC, Source.PLAIN);

        assertThat(chunks.getFirst().content()).contains("시간제취업", "활동 허가");
    }

    @Test
    void emptyTextGivesNoChunks() {
        assertThat(chunker.split(List.of(page(1, "  \n "), page(2, "")), DOC)).isEmpty();
    }

    /** 열 이름, 값, 열 이름, 값 … */
    private static TableRow row(int page, String... nameValues) {
        List<TableField> fields = new java.util.ArrayList<>();
        for (int i = 0; i < nameValues.length; i += 2) {
            fields.add(new TableField(nameValues[i], nameValues[i + 1]));
        }
        return new TableRow(fields, page);
    }

    private static PageText page(int page, String text) {
        return new PageText(page, text);
    }

    /** 태그 없는 PDF 의 줄 (글자 크기 pt) */
    private static TextLine plain(String text, int page, float fontSize) {
        return new TextLine(text, page, false, fontSize, false, false);
    }

    /** 태그 PDF 의 문단 (글자 크기 pt, 굵기) */
    private static TextLine tagged(String text, int page, float fontSize, boolean bold) {
        return new TextLine(text, page, true, fontSize, bold, false);
    }

    /** 마지막 체류자격 장 뒤에 묶음 문서가 붙은 문서 (본문 12pt) */
    private List<Chunk> bundled(TextLine... annex) {
        List<ContentBlock> blocks = new java.util.ArrayList<>(List.of(
                plain("관광취업(H-1)", 522, 20), plain(filler("관광취업 안내"), 522, 12)));
        blocks.addAll(List.of(annex));
        return chunker.splitBlocks(blocks, DOC, Source.PLAIN);
    }

    private static String filler(String subject) {
        return IntStream.rangeClosed(1, 18)
                .mapToObj(i -> subject + " " + i + "번 문장은 본문 내용을 설명합니다.")
                .collect(Collectors.joining(" "));
    }

    private static Chunk chunkWith(List<Chunk> chunks, String text) {
        return chunks.stream().filter(chunk -> chunk.body().contains(text)).findFirst().orElseThrow();
    }

    private static String body(Chunk chunk) {
        return chunk.content().substring(chunk.content().indexOf('\n') + 1);
    }
}
