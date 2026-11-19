package com.siot.backend.rag.document;

import com.siot.backend.rag.document.ContentBlock.TableBlock;
import com.siot.backend.rag.document.ContentBlock.TableRow;
import com.siot.backend.rag.document.ContentBlock.TextLine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ADM-04 [SPLIT]: 쪽별 텍스트를 검색용 조각으로 나눈다 (챗봇과 합의한 규칙).
 * <ul>
 *     <li>목표 {@value #TARGET}자, 최대 {@value #MAX}자 (첫 줄 제목 포함), 같은 절 안에서 앞 조각 끝 약 {@value #OVERLAP}자를 겹친다</li>
 *     <li>절 제목이 바뀌면 새 조각. 조각 첫 줄은 절 제목 ("상위 &gt; 하위"). 제목 전 내용은 문서 이름</li>
 *     <li>문단 경계에서 자르고, 한 문단이 너무 길면 문장 경계, 그래도 길면 공백에서 자른다</li>
 *     <li>제목은 짧고({@value #HEADING_MAX}자 이하) 문장부호로 끝나지 않으며, 앞 내용이 끝난 뒤(쪽 첫 줄, 빈 줄·문장·목록 줄 뒤)에 오고,
 *         다음 줄이 본문인 줄만. ■ 와 "1-4" 번호가 붙은 줄은 짧기만 하면 제목이다.
 *         같은 번호 모양이 앞뒤 줄에 이어지면 목록 항목으로 본다 ("1. 여권 / 2. 사진")</li>
 *     <li>상위 제목("1-4")이 바뀌면 그 아래 제목(■)도 초기화되어, 이전 절 제목이 다음 절로 이어지지 않는다</li>
 *     <li>표는 1차로 문단 기준으로 다룬다. 숫자·짧은 칸이 반복되는 줄이 이어지면 한 덩어리로 묶고 {@link Chunk#tableLike()} 로 표시한다</li>
 * </ul>
 */
public class DocumentChunker {

    public static final int TARGET = 600;
    public static final int MAX = 1000;
    public static final int OVERLAP = 100;
    static final int HEADING_MAX = 40;
    /** 표로 볼 연속 줄 수 */
    static final int TABLE_MIN_ROWS = 3;
    private static final int MIN_BUDGET = 200;
    private static final int TITLE_LEVELS = 2;

    private static final Pattern SENTENCE_END = Pattern.compile("[.!?。！？]$");
    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("(?<=[.!?。！？])\\s+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern HAS_LETTER = Pattern.compile("[\\p{L}]");
    private static final Pattern DIGIT = Pattern.compile("\\d");
    /** 목록 기호 (제목으로 보지 않음) */
    private static final Pattern BULLET = Pattern.compile("^[-•·※○◦▪∙*]\\s*");
    /** 다음 줄로 이어지는 문장 끝 (조사·연결 어미) */
    private static final Pattern CONTINUES = Pattern.compile("[을를은는에와과며면및]$");
    /** 장-절 번호만 있는 줄 (태그 PDF 에서 제목과 따로 놓인 번호) */
    private static final Pattern SECTION_NUMBER_ONLY = Pattern.compile("^\\d{1,2}-\\d{1,2}$");
    /** 번호 없는 제목이 될 수 없는 줄: "항목 : 값", 괄호로 시작, 주소 */
    private static final Pattern NOT_A_TITLE = Pattern.compile("\\s:\\s|^[(\\[]|www\\.|https?://");
    /** 다음 줄로 이어지는 목록 줄 끝 (쉼표, 가운뎃점, 여는 괄호, 콜론, 줄표) */
    private static final Pattern LIST_CONTINUES = Pattern.compile("[,·(:\\-–]\\s*$");

    /**
     * 번호·기호 모양. level 이 작을수록 상위 제목이다 (상위 제목이 바뀌면 그 아래 제목은 초기화).
     * strong 인 모양(■, "1-4")은 짧기만 하면 앞뒤 줄과 상관없이 제목으로 본다.
     */
    enum Marker {
        CHAPTER(1, "^(?:제\\s*\\d+\\s*[장편부]|PART\\s*\\d+)", false),
        /** "1-4 지방세 납부" (장-절 번호) */
        SECTION_NUM(2, "^\\d{1,2}-\\d{1,2}\\s+", true),
        SECTION(2, "^제\\s*\\d+\\s*[절조]", false),
        ROMAN(2, "^(?:[ⅠⅡⅢⅣⅤⅥⅦⅧⅨⅩ]|[IVX]{1,4}\\.)\\s*", false),
        SQUARE(3, "^■\\s*", true),
        SYMBOL(3, "^[□▶▷●◆◇◎]\\s*", false),
        NUM_DOT(3, "^\\d{1,2}\\.\\s+", false),
        HANGUL_DOT(4, "^[가-하]\\.\\s*", false),
        NUM_PAREN(5, "^\\d{1,2}\\)\\s*", false),
        PAREN_NUM(5, "^\\(\\d{1,2}\\)\\s*", false),
        CIRCLED(6, "^[①-⑳]\\s*", false),
        NONE(3, null, false);

        final int level;
        final Pattern pattern;
        final boolean strong;

        Marker(int level, String regex, boolean strong) {
            this.level = level;
            this.pattern = regex == null ? null : Pattern.compile(regex);
            this.strong = strong;
        }

        static Marker of(String line) {
            for (Marker marker : values()) {
                if (marker.pattern != null && marker.pattern.matcher(line).find()) {
                    return marker;
                }
            }
            return NONE;
        }
    }

    /**
     * @param chunkNo   버전 안에서 1부터
     * @param pageStart 조각 내용이 시작하는 쪽 (1부터)
     * @param pageEnd   조각 내용이 끝나는 쪽
     * @param content   첫 줄 제목 + 본문
     * @param title     첫 줄 제목 (없으면 null)
     * @param tableLike       표가 들어 있음 (확인용, 저장하지 않음). 태그 PDF 의 표이거나, 표로 보이는 줄
     * @param structuredTable 태그 PDF 의 표 (Table 태그) 조각
     */
    public record Chunk(int chunkNo, int pageStart, int pageEnd, String content, String title, boolean tableLike,
                        boolean structuredTable) {
    }

    /**
     * @param pageStart  쪽의 첫 줄 (앞 줄과 쪽이 다름)
     * @param table      숫자 칸이 많은 줄 (표의 행)
     * @param header     짧은 칸만 여러 개인 줄. 바로 아래가 표의 행이면 표의 머리 행
     * @param tableBlock 태그 PDF 의 표 자리 (text 는 빈 문자열)
     */
    private record Line(String text, int page, boolean blankBefore, boolean pageStart, Marker marker,
                        boolean table, boolean header, TableBlock tableBlock) {
    }

    /** 문단: 줄을 이은 텍스트와, 글자 위치 → 쪽 번호를 찾기 위한 줄 시작 위치 */
    private static final class Paragraph {
        final StringBuilder text = new StringBuilder();
        final List<Integer> lineStarts = new ArrayList<>();
        final List<Integer> linePages = new ArrayList<>();
        final boolean table;

        Paragraph(boolean table) {
            this.table = table;
        }

        void add(Line line) {
            add(line.text(), line.page());
        }

        void add(String lineText, int page) {
            if (!text.isEmpty()) {
                text.append(table ? '\n' : ' ');
            }
            lineStarts.add(text.length());
            linePages.add(page);
            text.append(lineText);
        }

        int pageAt(int offset) {
            int page = linePages.getFirst();
            for (int i = 0; i < lineStarts.size() && lineStarts.get(i) <= offset; i++) {
                page = linePages.get(i);
            }
            return page;
        }
    }

    /** 조각에 넣는 단위 (문단 또는 긴 문단을 자른 조각) */
    private record Unit(String text, int pageStart, int pageEnd, boolean table) {
    }

    /** 절: 제목과, 순서대로의 문단({@link Paragraph})·표({@link TableBlock}) */
    private record Section(String title, List<Object> items) {
    }

    /**
     * 태그 없는 PDF 의 쪽 텍스트 (줄 단위).
     *
     * @param fallbackTitle 첫 제목 전 내용의 첫 줄 (보통 문서 이름). null 이면 제목 줄 없음
     */
    public List<Chunk> split(List<PageText> pages, String fallbackTitle) {
        return splitBlocks(PdfContentExtractor.toBlocks(pages), fallbackTitle);
    }

    public List<Chunk> split(ExtractedContent content, String fallbackTitle) {
        return splitBlocks(content.blocks(), fallbackTitle);
    }

    /**
     * @param blocks        읽는 순서대로의 텍스트 줄·표
     * @param fallbackTitle 첫 제목 전 내용의 첫 줄 (보통 문서 이름). null 이면 제목 줄 없음
     */
    public List<Chunk> splitBlocks(List<ContentBlock> blocks, String fallbackTitle) {
        List<Line> lines = toLines(blocks);
        boolean[] headings = findHeadings(lines);
        List<Section> sections = toSections(lines, headings, fallbackTitle);

        List<Chunk> chunks = new ArrayList<>();
        for (Section section : sections) {
            pack(section, chunks);
        }
        return chunks;
    }

    // ---------------------------------------------------------------- 줄

    /** 표는 빈 줄(text "")로 자리만 잡는다: 제목 판단에서 본문으로 보이고, 절에 표로 들어간다 */
    private static List<Line> toLines(List<ContentBlock> blocks) {
        List<Line> lines = new ArrayList<>();
        int previousPage = -1;
        for (ContentBlock block : blocks) {
            switch (block) {
                case TextLine textLine -> {
                    String text = textLine.text().replace(' ', ' ').strip();
                    if (text.isEmpty()) {
                        continue;
                    }
                    Marker marker = Marker.of(text);
                    lines.add(new Line(text, textLine.page(), textLine.newParagraph(), textLine.page() != previousPage,
                            marker, isTableRow(text), marker == Marker.NONE && isHeaderRow(text), null));
                    previousPage = textLine.page();
                }
                case TableBlock table -> {
                    if (table.rows().isEmpty()) {
                        continue;
                    }
                    int page = table.firstPage();
                    lines.add(new Line("", page, true, page != previousPage, Marker.NONE, false, false, table));
                    previousPage = table.rows().getLast().page();
                }
            }
        }
        return mergeSectionNumbers(lines);
    }

    /**
     * 번호만 있는 줄("1-4")을 같은 쪽의 짧은 제목 줄과 합친다 ("1-4 지방세 납부").
     * 책자 디자인에 따라 번호가 제목 앞이나 뒤에 따로 놓이므로, 앞 줄을 먼저 보고 안 되면 다음 줄을 본다.
     */
    private static List<Line> mergeSectionNumbers(List<Line> lines) {
        List<Line> merged = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            if (!SECTION_NUMBER_ONLY.matcher(line.text()).matches()) {
                merged.add(line);
                continue;
            }
            Line previous = merged.isEmpty() ? null : merged.getLast();
            Line next = i + 1 < lines.size() ? lines.get(i + 1) : null;
            if (isPlainTitle(previous, line.page())) {
                merged.set(merged.size() - 1, withText(previous, line.text() + " " + previous.text()));
            } else if (isPlainTitle(next, line.page())) {
                merged.add(withText(new Line(next.text(), line.page(), line.blankBefore(), line.pageStart(),
                        next.marker(), false, false, null), line.text() + " " + next.text()));
                i++;
            } else {
                merged.add(line);
            }
        }
        return merged;
    }

    private static boolean isPlainTitle(Line line, int page) {
        return line != null && line.page() == page && line.tableBlock() == null
                && line.marker() == Marker.NONE && isHeadingCandidate(line);
    }

    private static Line withText(Line line, String text) {
        Marker marker = Marker.of(text);
        return new Line(text, line.page(), line.blankBefore(), line.pageStart(), marker, false, false, null);
    }

    /** 칸이 3개 이상이고 그중 40% 이상에 숫자가 있는 줄 (예: "외국인등록 30,000원 3주") */
    static boolean isTableRow(String line) {
        if (SENTENCE_END.matcher(line).find()) {
            return false;
        }
        String[] cells = WHITESPACE.split(line);
        if (cells.length < 3) {
            return false;
        }
        int digitCells = 0;
        for (String cell : cells) {
            if (DIGIT.matcher(cell).find()) {
                digitCells++;
            }
        }
        return digitCells * 10 >= cells.length * 4;
    }

    /** 짧은 칸(4자 이하)만 3개 이상인 줄 (예: "구분 수수료 처리 기간") */
    static boolean isHeaderRow(String line) {
        if (SENTENCE_END.matcher(line).find()) {
            return false;
        }
        String[] cells = WHITESPACE.split(line);
        if (cells.length < 3) {
            return false;
        }
        for (String cell : cells) {
            if (cell.length() > 4) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- 제목

    private static boolean[] findHeadings(List<Line> lines) {
        int n = lines.size();
        boolean[] heading = new boolean[n];
        // 뒤에서부터: "2. 신청 / 가. 기한 / 본문" 처럼 제목이 이어지면 다음 줄이 제목이어도 된다
        for (int i = n - 1; i >= 0; i--) {
            Line line = lines.get(i);
            if (i + 1 >= n || !isHeadingCandidate(line) || isListItem(lines, i)) {
                continue;
            }
            // ■ 와 "1-4" 는 짧기만 하면 제목
            if (line.marker().strong) {
                heading[i] = true;
                continue;
            }
            if (!startsBlock(lines, i)) {
                continue;
            }
            Line next = lines.get(i + 1);
            if (line.header() && next.table()) {
                continue;
            }
            boolean nextIsBody = !isHeadingCandidate(next);
            boolean nextIsSubHeading = heading[i + 1] && next.marker() != line.marker();
            heading[i] = nextIsBody || nextIsSubHeading;
        }
        return heading;
    }

    private static boolean isHeadingCandidate(Line line) {
        String text = line.text();
        return text.length() <= HEADING_MAX
                && !SENTENCE_END.matcher(text).find()
                && !text.endsWith(":") && !text.endsWith(",")
                && !BULLET.matcher(text).find()
                && !line.table()
                && HAS_LETTER.matcher(text).find()
                // 번호 없는 줄이 조사로 끝나면 문장이 다음 줄로 이어지는 것 ("... 건강보험에")
                && !(line.marker() == Marker.NONE && CONTINUES.matcher(text).find())
                // 번호 없는 줄이 숫자 위주면 통계·값 ("514,331명", "101개교")
                && !(line.marker() == Marker.NONE && mostlyDigits(text))
                // 번호 없는 줄이 "항목 : 값", 괄호 설명, 주소(URL)면 제목이 아니다
                && !(line.marker() == Marker.NONE && NOT_A_TITLE.matcher(text).find());
    }

    private static boolean mostlyDigits(String text) {
        int letters = 0;
        int digits = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isDigit(c)) {
                digits++;
            } else if (Character.isLetter(c)) {
                letters++;
            }
        }
        return letters < 2 || digits > letters;
    }

    /** 앞이나 뒤 줄이 같은 번호 모양이면 목록 항목 */
    private static boolean isListItem(List<Line> lines, int i) {
        Marker marker = lines.get(i).marker();
        if (marker == Marker.NONE || marker == Marker.CHAPTER || marker == Marker.SECTION) {
            return false;
        }
        boolean prevSame = i > 0 && lines.get(i - 1).marker() == marker;
        boolean nextSame = i + 1 < lines.size() && lines.get(i + 1).marker() == marker;
        return prevSame || nextSame;
    }

    /**
     * 제목은 앞 내용이 끝난 뒤에 온다 (문서 처음, 쪽 첫 줄, 빈 줄 뒤, 문장 끝 뒤, 목록 줄 뒤).
     * 번호가 있는 제목은 다른 모양의 번호 제목 바로 뒤에도 올 수 있다 ("2. 외국인등록 / 가. 신청 기한").
     */
    private static boolean startsBlock(List<Line> lines, int i) {
        Line line = lines.get(i);
        if (i == 0 || line.blankBefore() || line.pageStart()) {
            return true;
        }
        Line prev = lines.get(i - 1);
        if (prev.tableBlock() != null || SENTENCE_END.matcher(prev.text()).find()) {
            return true;
        }
        // "· 문의 : … ☎(031)310-2089" 처럼 마침표 없이 끝나는 목록 줄 다음.
        // "· 납부대상 : … 이륜차," 처럼 다음 줄로 이어지는 목록 줄 다음은 아니다
        if (BULLET.matcher(prev.text()).find() && !LIST_CONTINUES.matcher(prev.text()).find()
                && !CONTINUES.matcher(prev.text()).find()) {
            return true;
        }
        Marker marker = lines.get(i).marker();
        return marker != Marker.NONE && prev.marker() != Marker.NONE && prev.marker() != marker
                && isHeadingCandidate(prev);
    }

    // ---------------------------------------------------------------- 절·문단

    private static List<Section> toSections(List<Line> lines, boolean[] headings, String fallbackTitle) {
        List<Section> sections = new ArrayList<>();
        Deque<Line> path = new ArrayDeque<>();
        Section section = new Section(fallbackTitle, new ArrayList<>());
        sections.add(section);
        Paragraph paragraph = null;
        Line prev = null;

        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            if (headings[i]) {
                while (!path.isEmpty() && path.peekLast().marker().level >= line.marker().level) {
                    path.removeLast();
                }
                path.addLast(line);
                section = new Section(title(path), new ArrayList<>());
                sections.add(section);
                paragraph = null;
                prev = line;
                continue;
            }
            if (line.tableBlock() != null) {
                section.items().add(line.tableBlock());
                paragraph = null;
                prev = line;
                continue;
            }
            boolean tableRow = (line.table() && inTableRun(lines, headings, i))
                    || (line.header() && i + 1 < lines.size() && lines.get(i + 1).table()
                    && inTableRun(lines, headings, i + 1));
            if (paragraph == null || startsParagraph(prev, line, paragraph, tableRow)) {
                paragraph = new Paragraph(tableRow);
                section.items().add(paragraph);
            }
            paragraph.add(line);
            prev = line;
        }
        return sections;
    }

    private static boolean startsParagraph(Line prev, Line line, Paragraph current, boolean tableRow) {
        if (tableRow != current.table) {
            return true;
        }
        if (tableRow) {
            return false;
        }
        return prev.tableBlock() != null
                || line.blankBefore()
                || SENTENCE_END.matcher(prev.text()).find()
                || line.marker() != Marker.NONE
                || BULLET.matcher(line.text()).find();
    }

    /** 표 줄이 TABLE_MIN_ROWS 줄 이상 이어지는 구간에 있는지 */
    private static boolean inTableRun(List<Line> lines, boolean[] headings, int i) {
        int start = i;
        while (start > 0 && lines.get(start - 1).table() && !headings[start - 1]) {
            start--;
        }
        int end = i;
        while (end + 1 < lines.size() && lines.get(end + 1).table() && !headings[end + 1]) {
            end++;
        }
        return end - start + 1 >= TABLE_MIN_ROWS;
    }

    private static String title(Deque<Line> path) {
        List<String> parts = new ArrayList<>();
        path.descendingIterator().forEachRemaining(line -> {
            if (parts.size() < TITLE_LEVELS) {
                parts.addFirst(line.text());
            }
        });
        return String.join(" > ", parts);
    }

    // ---------------------------------------------------------------- 조각

    /** 문단은 목표 길이로 묶고(겹침 있음), 표는 앞뒤 문단과 떼어 따로 조각으로 만든다 */
    private static void pack(Section section, List<Chunk> chunks) {
        String title = section.title();
        int titleLength = title == null ? 0 : title.length() + 1;
        int budget = Math.max(MIN_BUDGET, MAX - titleLength);
        int target = Math.max(MIN_BUDGET / 2, TARGET - titleLength);

        List<Unit> units = new ArrayList<>();
        for (Object item : section.items()) {
            if (item instanceof Paragraph paragraph) {
                units.addAll(toUnits(paragraph, budget));
            } else if (item instanceof TableBlock table) {
                packText(units, title, budget, target, chunks);
                units.clear();
                packTable(table, title, budget, chunks);
            }
        }
        packText(units, title, budget, target, chunks);
    }

    /**
     * 표 하나를 한 조각으로 (한 줄에 한 행, 행마다 열 이름). 길면 행 경계에서 나눈다.
     * 행마다 열 이름이 붙어 있어 앞 조각 없이도 읽히므로 겹치지 않는다.
     */
    private static void packTable(TableBlock table, String title, int budget, List<Chunk> chunks) {
        List<Unit> current = new ArrayList<>();
        for (TableRow row : table.rows()) {
            Paragraph rowText = new Paragraph(true);
            rowText.add(row.render(), row.page());
            for (Unit unit : toUnits(rowText, budget)) {
                if (!current.isEmpty() && length(current) + 1 + unit.text().length() > budget) {
                    emit(current, title, chunks, true);
                    current = new ArrayList<>();
                }
                current.add(unit);
            }
        }
        if (!current.isEmpty()) {
            emit(current, title, chunks, true);
        }
    }

    private static void packText(List<Unit> units, String title, int budget, int target, List<Chunk> chunks) {
        List<Unit> current = new ArrayList<>();
        Unit carry = null;
        for (Unit unit : units) {
            if (current.isEmpty()) {
                if (carry != null && carry.text().length() + 1 + unit.text().length() <= budget) {
                    current.add(carry);
                }
                current.add(unit);
                continue;
            }
            int length = length(current) + 1 + unit.text().length();
            boolean fits = length <= target || (length(current) < target / 2 && length <= budget);
            if (fits) {
                current.add(unit);
            } else {
                carry = tail(current);
                emit(current, title, chunks, false);
                current = new ArrayList<>();
                if (carry != null && carry.text().length() + 1 + unit.text().length() <= budget) {
                    current.add(carry);
                }
                current.add(unit);
            }
        }
        if (!current.isEmpty()) {
            emit(current, title, chunks, false);
        }
    }

    /** 긴 문단은 문장 경계(없으면 공백)에서 budget 이하로 자른다 */
    private static List<Unit> toUnits(Paragraph paragraph, int budget) {
        String text = paragraph.text.toString();
        if (text.length() <= budget) {
            return List.of(new Unit(text, paragraph.linePages.getFirst(), paragraph.linePages.getLast(), paragraph.table));
        }
        List<int[]> pieces = new ArrayList<>();
        int pieceStart = 0;
        int lastBoundary = -1;
        Matcher matcher = (paragraph.table ? Pattern.compile("\n") : SENTENCE_BOUNDARY).matcher(text);
        List<Integer> boundaries = new ArrayList<>();
        while (matcher.find()) {
            boundaries.add(matcher.start());
        }
        boundaries.add(text.length());
        for (int boundary : boundaries) {
            if (boundary - pieceStart > TARGET && lastBoundary > pieceStart) {
                pieces.add(new int[]{pieceStart, lastBoundary});
                pieceStart = skipSpace(text, lastBoundary);
            }
            while (boundary - pieceStart > budget) {
                int cut = text.lastIndexOf(' ', pieceStart + budget);
                if (cut <= pieceStart) {
                    cut = pieceStart + budget;
                }
                pieces.add(new int[]{pieceStart, cut});
                pieceStart = skipSpace(text, cut);
            }
            lastBoundary = boundary;
        }
        if (pieceStart < text.length()) {
            pieces.add(new int[]{pieceStart, text.length()});
        }

        List<Unit> units = new ArrayList<>(pieces.size());
        for (int[] piece : pieces) {
            String pieceText = text.substring(piece[0], piece[1]).strip();
            if (!pieceText.isEmpty()) {
                units.add(new Unit(pieceText, paragraph.pageAt(piece[0]),
                        paragraph.pageAt(Math.max(piece[0], piece[1] - 1)), paragraph.table));
            }
        }
        return units;
    }

    private static int skipSpace(String text, int index) {
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return index;
    }

    /** 앞 조각 끝의 약 OVERLAP 자: 끝 문장들, 마지막 문장이 길면 마지막 OVERLAP 자 (단어 경계부터) */
    private static Unit tail(List<Unit> units) {
        Unit last = units.getLast();
        String text = last.text();
        String[] sentences = SENTENCE_BOUNDARY.split(text);
        StringBuilder tail = new StringBuilder();
        for (int i = sentences.length - 1; i >= 0; i--) {
            String sentence = sentences[i].strip();
            int length = tail.isEmpty() ? sentence.length() : sentence.length() + 1 + tail.length();
            if (length > OVERLAP) {
                break;
            }
            tail.insert(0, tail.isEmpty() ? sentence : sentence + " ");
        }
        if (tail.isEmpty()) {
            int from = text.length() - OVERLAP;
            if (from <= 0) {
                tail.append(text);
            } else {
                int space = text.indexOf(' ', from);
                tail.append(space < 0 || space >= text.length() - 1 ? text.substring(from) : text.substring(space + 1));
            }
        }
        if (tail.length() >= text.length() && units.size() == 1) {
            // 조각 전체가 겹침이 되면 같은 내용이 반복되므로 겹치지 않는다
            return null;
        }
        return new Unit(tail.toString(), last.pageEnd(), last.pageEnd(), last.table());
    }

    private static int length(List<Unit> units) {
        int length = -1;
        for (Unit unit : units) {
            length += unit.text().length() + 1;
        }
        return length;
    }

    private static void emit(List<Unit> units, String title, List<Chunk> chunks, boolean structuredTable) {
        StringBuilder body = new StringBuilder();
        int pageStart = Integer.MAX_VALUE;
        int pageEnd = 0;
        boolean table = false;
        for (Unit unit : units) {
            if (!body.isEmpty()) {
                body.append('\n');
            }
            body.append(unit.text());
            pageStart = Math.min(pageStart, unit.pageStart());
            pageEnd = Math.max(pageEnd, unit.pageEnd());
            table |= unit.table();
        }
        String content = title == null ? body.toString() : title + "\n" + body;
        chunks.add(new Chunk(chunks.size() + 1, pageStart, pageEnd, content, title, table, structuredTable));
    }
}
