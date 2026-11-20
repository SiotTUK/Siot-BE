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

    private static String body(Chunk chunk) {
        return chunk.content().substring(chunk.content().indexOf('\n') + 1);
    }
}
