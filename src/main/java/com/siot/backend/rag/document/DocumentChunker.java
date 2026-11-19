package com.siot.backend.rag.document;

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
 *     <li>제목은 짧고({@value #HEADING_MAX}자 이하) 문장부호로 끝나지 않으며, 앞 내용이 끝난 뒤에 오고, 다음 줄이 본문인 줄만.
 *         같은 번호 모양이 앞뒤 줄에 이어지면 목록 항목으로 본다 ("1. 여권 / 2. 사진")</li>
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
    private static final Pattern CONTINUES = Pattern.compile("[을를은는에의와과며면및]$");

    /** 번호·기호 모양. 순서가 곧 제목 단계 (작을수록 상위) */
    enum Marker {
        CHAPTER(1, "^제\\s*\\d+\\s*[장편부]"),
        SECTION(2, "^제\\s*\\d+\\s*[절조]"),
        ROMAN(2, "^(?:[ⅠⅡⅢⅣⅤⅥⅦⅧⅨⅩ]|[IVX]{1,4}\\.)\\s*"),
        SYMBOL(3, "^[■□▶▷●◆◇◎]\\s*"),
        NUM_DOT(3, "^\\d{1,2}\\.\\s+"),
        HANGUL_DOT(4, "^[가-하]\\.\\s*"),
        NUM_PAREN(5, "^\\d{1,2}\\)\\s*"),
        PAREN_NUM(5, "^\\(\\d{1,2}\\)\\s*"),
        CIRCLED(6, "^[①-⑳]\\s*"),
        NONE(3, null);

        final int level;
        final Pattern pattern;

        Marker(int level, String regex) {
            this.level = level;
            this.pattern = regex == null ? null : Pattern.compile(regex);
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
     * @param tableLike 표로 보이는 줄이 들어 있음 (확인용, 저장하지 않음)
     */
    public record Chunk(int chunkNo, int pageStart, int pageEnd, String content, String title, boolean tableLike) {
    }

    /**
     * @param table  숫자 칸이 많은 줄 (표의 행)
     * @param header 짧은 칸만 여러 개인 줄. 바로 아래가 표의 행이면 표의 머리 행
     */
    private record Line(String text, int page, boolean blankBefore, Marker marker, boolean table, boolean header) {
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
            if (!text.isEmpty()) {
                text.append(table ? '\n' : ' ');
            }
            lineStarts.add(text.length());
            linePages.add(line.page());
            text.append(line.text());
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

    private record Section(String title, List<Paragraph> paragraphs) {
    }

    /**
     * @param fallbackTitle 첫 제목 전 내용의 첫 줄 (보통 문서 이름). null 이면 제목 줄 없음
     */
    public List<Chunk> split(List<PageText> pages, String fallbackTitle) {
        List<Line> lines = toLines(pages);
        boolean[] headings = findHeadings(lines);
        List<Section> sections = toSections(lines, headings, fallbackTitle);

        List<Chunk> chunks = new ArrayList<>();
        for (Section section : sections) {
            pack(section, chunks);
        }
        return chunks;
    }

    // ---------------------------------------------------------------- 줄

    private static List<Line> toLines(List<PageText> pages) {
        List<Line> lines = new ArrayList<>();
        boolean blank = true;
        for (PageText page : pages) {
            for (String raw : page.text().split("\n")) {
                String text = raw.replace(' ', ' ').strip();
                if (text.isEmpty()) {
                    blank = true;
                    continue;
                }
                Marker marker = Marker.of(text);
                lines.add(new Line(text, page.page(), blank, marker,
                        isTableRow(text), marker == Marker.NONE && isHeaderRow(text)));
                blank = false;
            }
        }
        return lines;
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
            if (i + 1 >= n || !isHeadingCandidate(line) || isListItem(lines, i) || !startsBlock(lines, i)) {
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
                && !(line.marker() == Marker.NONE && CONTINUES.matcher(text).find());
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
     * 제목은 앞 내용이 끝난 뒤에 온다 (문서 처음, 빈 줄 뒤, 문장 끝 뒤).
     * 번호가 있는 제목은 다른 모양의 번호 제목 바로 뒤에도 올 수 있다 ("2. 외국인등록 / 가. 신청 기한").
     */
    private static boolean startsBlock(List<Line> lines, int i) {
        if (i == 0 || lines.get(i).blankBefore()) {
            return true;
        }
        Line prev = lines.get(i - 1);
        if (SENTENCE_END.matcher(prev.text()).find()) {
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
            boolean tableRow = (line.table() && inTableRun(lines, headings, i))
                    || (line.header() && i + 1 < lines.size() && lines.get(i + 1).table()
                    && inTableRun(lines, headings, i + 1));
            if (paragraph == null || startsParagraph(prev, line, paragraph, tableRow)) {
                paragraph = new Paragraph(tableRow);
                section.paragraphs().add(paragraph);
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
        return line.blankBefore()
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

    private static void pack(Section section, List<Chunk> chunks) {
        String title = section.title();
        int titleLength = title == null ? 0 : title.length() + 1;
        int budget = Math.max(MIN_BUDGET, MAX - titleLength);
        int target = Math.max(MIN_BUDGET / 2, TARGET - titleLength);

        List<Unit> units = new ArrayList<>();
        for (Paragraph paragraph : section.paragraphs()) {
            units.addAll(toUnits(paragraph, budget));
        }

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
                emit(current, title, chunks);
                current = new ArrayList<>();
                if (carry != null && carry.text().length() + 1 + unit.text().length() <= budget) {
                    current.add(carry);
                }
                current.add(unit);
            }
        }
        if (!current.isEmpty()) {
            emit(current, title, chunks);
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

    private static void emit(List<Unit> units, String title, List<Chunk> chunks) {
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
        chunks.add(new Chunk(chunks.size() + 1, pageStart, pageEnd, content, title, table));
    }
}
