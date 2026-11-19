package com.siot.backend.rag.document;

import com.siot.backend.rag.document.DocumentChunker.Chunk;
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
    void emptyTextGivesNoChunks() {
        assertThat(chunker.split(List.of(page(1, "  \n "), page(2, "")), DOC)).isEmpty();
    }

    private static PageText page(int page, String text) {
        return new PageText(page, text);
    }

    private static String body(Chunk chunk) {
        return chunk.content().substring(chunk.content().indexOf('\n') + 1);
    }
}
