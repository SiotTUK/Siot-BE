package com.siot.backend.rag.document;

import com.siot.backend.rag.document.ContentBlock.TableBlock;
import com.siot.backend.rag.document.ContentBlock.TableField;
import com.siot.backend.rag.document.ContentBlock.TableRow;
import com.siot.backend.rag.document.ContentBlock.TextLine;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDAttributeObject;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDMarkedContentReference;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureNode;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;
import org.apache.pdfbox.pdmodel.documentinterchange.markedcontent.PDMarkedContent;
import org.apache.pdfbox.pdmodel.documentinterchange.taggedpdf.PDTableAttributeObject;
import org.apache.pdfbox.text.PDFMarkedContentExtractor;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * ADM-04 [EXTRACT]: 태그 PDF 를 구조 트리 순서로 추출한다.
 * <ul>
 *     <li>머리말·꼬리말·쪽 번호는 Artifact 로 표시되어 구조 트리에 없으므로 자동으로 빠진다</li>
 *     <li>문단(P·H·LBody 등, 역할 맵 적용)은 쪽마다 {@link TextLine} 하나. 쪽을 넘는 문단은 쪽마다 나눠 쪽 번호를 정확히 한다</li>
 *     <li>표(Table/TR/TD)는 행 단위 {@link TableBlock}. RowSpan 칸은 아래 행에도 채우고, ColSpan 칸은 한 번만 쓴다.
 *         첫 행(과 첫 행이 RowSpan 으로 이어지는 행)을 머리 행으로 보고 열 이름으로 쓴다</li>
 *     <li>띄어쓰기: 글자 사이 간격과 줄 바뀜을 보고 공백을 넣는다 (공백 글자가 없는 PDF 도 있다)</li>
 * </ul>
 */
public class TaggedPdfExtractor {

    /** 한 문단으로 다루는 표준 구조 유형 */
    private static final Set<String> PARAGRAPH_TYPES = Set.of(
            "P", "H", "H1", "H2", "H3", "H4", "H5", "H6", "LI", "Lbl", "LBody", "Caption", "TOCI", "Note",
            "BlockQuote", "Quote", "Code", "Formula");
    private static final Set<String> CELL_TYPES = Set.of("TD", "TH");
    /** 머리 행에 올 수 없는 값: 전화, URL, 도로명 주소("○○로 12", "○○길 3"), 전화번호 모양 */
    private static final Pattern VALUE_LIKE = Pattern.compile("☎|www\\.|https?://|[로길]\\s?\\d|\\d{2,}-\\d{3,}");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    /** 강제 줄바꿈으로 보는 줄 첫머리 (목록·번호 기호) */
    private static final Pattern LINE_START_MARKER = Pattern.compile(
            "^(?:[-•·※○◦▪∙*■□▶▷●◆◇◎★☞①-⑳]|\\d{1,2}[.)]\\s|\\(\\d{1,2}\\)|[가-하]\\.\\s|\\d{1,2}-\\d{1,2}\\s)");

    /** (쪽, MCID) 의 글자 */
    private Map<COSDictionary, Map<Integer, List<TextPosition>>> glyphs;
    private Map<COSDictionary, Integer> pageNumbers;
    private List<ContentBlock> blocks;
    private int artifactLength;
    /** 구조 트리가 가리킨 (쪽, MCID) 와 그 글자 수. 표 RowSpan 처럼 같은 글자를 여러 번 써도 한 번만 센다 */
    private Set<Long> usedContent;
    private int usedLength;

    /**
     * @param blocks         구조 트리 순서의 문단·표
     * @param usedLength     구조 트리가 가리킨 글자 수 (공백 제외, 중복 없이)
     * @param artifactLength Artifact(머리말·꼬리말·쪽 번호) 글자 수 (공백 제외). 내용 비율 계산에서 뺀다
     */
    public record Result(List<ContentBlock> blocks, int usedLength, int artifactLength) {
    }

    /** 태그 PDF 인지 (MarkInfo/Marked 이고 구조 트리가 있음) */
    public static boolean isTagged(PDDocument document) {
        return document.getDocumentCatalog().getMarkInfo() != null
                && document.getDocumentCatalog().getMarkInfo().isMarked()
                && document.getDocumentCatalog().getStructureTreeRoot() != null;
    }

    public synchronized Result extract(PDDocument document) throws IOException {
        PDStructureTreeRoot root = document.getDocumentCatalog().getStructureTreeRoot();
        if (root == null) {
            return new Result(List.of(), 0, 0);
        }
        artifactLength = 0;
        usedLength = 0;
        usedContent = new java.util.HashSet<>();
        indexGlyphs(document);
        blocks = new ArrayList<>();
        visit(root, null);
        Result result = new Result(blocks, usedLength, artifactLength);
        glyphs = null;
        pageNumbers = null;
        blocks = null;
        usedContent = null;
        return result;
    }

    // ---------------------------------------------------------------- 글자 색인

    private void indexGlyphs(PDDocument document) throws IOException {
        glyphs = new IdentityHashMap<>();
        pageNumbers = new IdentityHashMap<>();
        int number = 1;
        for (PDPage page : document.getPages()) {
            pageNumbers.put(page.getCOSObject(), number++);
            PDFMarkedContentExtractor extractor = new PDFMarkedContentExtractor();
            extractor.processPage(page);
            Map<Integer, List<TextPosition>> byMcid = new HashMap<>();
            for (PDMarkedContent content : extractor.getMarkedContents()) {
                collect(content, -1, byMcid);
            }
            glyphs.put(page.getCOSObject(), byMcid);
        }
    }

    /** 표시된 내용의 글자를 가장 가까운 MCID 아래에 모은다. Artifact 는 글자 수만 센다 */
    private void collect(PDMarkedContent content, int parentMcid, Map<Integer, List<TextPosition>> byMcid) {
        int mcid = content.getMCID() >= 0 ? content.getMCID() : parentMcid;
        if ("Artifact".equals(content.getTag())) {
            artifactLength += countGlyphs(content);
            return;
        }
        for (Object item : content.getContents()) {
            if (item instanceof TextPosition position && mcid >= 0) {
                byMcid.computeIfAbsent(mcid, key -> new ArrayList<>()).add(position);
            } else if (item instanceof PDMarkedContent child) {
                collect(child, mcid, byMcid);
            }
        }
    }

    private static int countGlyphs(PDMarkedContent content) {
        int count = 0;
        for (Object item : content.getContents()) {
            if (item instanceof TextPosition position) {
                count += ExtractedContent.nonSpaceLength(position.getUnicode() == null ? "" : position.getUnicode());
            } else if (item instanceof PDMarkedContent child) {
                count += countGlyphs(child);
            }
        }
        return count;
    }

    // ---------------------------------------------------------------- 구조 트리

    /** 쪽마다 모은 글자 묶음 (구조 순서) */
    private record Run(int page, List<TextPosition> positions) {
    }

    private void visit(PDStructureNode node, PDPage inheritedPage) {
        for (Object kid : node.getKids()) {
            if (kid instanceof PDStructureElement element) {
                PDPage page = element.getPage() != null ? element.getPage() : inheritedPage;
                String type = standardType(element);
                if ("Table".equals(type)) {
                    addTable(element, page);
                } else if (PARAGRAPH_TYPES.contains(type) || ownsContent(element)) {
                    addParagraph(element, page);
                } else {
                    visit(element, page);
                }
            }
        }
    }

    /** 문단. 안에 표가 있으면 표 앞뒤로 나눈다 */
    private void addParagraph(PDStructureElement element, PDPage page) {
        List<Run> runs = new ArrayList<>();
        collectRuns(element, page, runs);
        addTextLines(runs);
    }

    private void collectRuns(PDStructureElement element, PDPage page, List<Run> runs) {
        for (Object kid : element.getKids()) {
            if (kid instanceof Integer mcid) {
                addRun(page, mcid, runs);
            } else if (kid instanceof PDMarkedContentReference reference) {
                addRun(reference.getPage() != null ? reference.getPage() : page, reference.getMCID(), runs);
            } else if (kid instanceof PDStructureElement child) {
                PDPage childPage = child.getPage() != null ? child.getPage() : page;
                if ("Table".equals(standardType(child))) {
                    addTextLines(runs);
                    runs.clear();
                    addTable(child, childPage);
                } else {
                    collectRuns(child, childPage, runs);
                }
            }
        }
    }

    private void addRun(PDPage page, int mcid, List<Run> runs) {
        if (page == null) {
            return;
        }
        List<TextPosition> positions = glyphs.getOrDefault(page.getCOSObject(), Map.of()).get(mcid);
        Integer number = pageNumbers.get(page.getCOSObject());
        if (positions == null || positions.isEmpty() || number == null) {
            return;
        }
        if (usedContent.add(((long) number << 32) | mcid)) {
            for (TextPosition position : positions) {
                usedLength += ExtractedContent.nonSpaceLength(clean(position.getUnicode()));
            }
        }
        if (!runs.isEmpty() && runs.getLast().page() == number) {
            runs.getLast().positions().addAll(positions);
        } else {
            runs.add(new Run(number, new ArrayList<>(positions)));
        }
    }

    /** 쪽마다, 그리고 문단 안 강제 줄바꿈마다 한 줄. 첫 줄만 새 문단 */
    private void addTextLines(List<Run> runs) {
        boolean first = true;
        for (Run run : runs) {
            for (String text : lines(run.positions())) {
                blocks.add(new TextLine(text, run.page(), first));
                first = false;
            }
        }
    }

    // ---------------------------------------------------------------- 표

    private record Cell(String text, int page, int rowSpan, int colSpan) {
    }

    private void addTable(PDStructureElement table, PDPage page) {
        List<List<Cell>> rows = new ArrayList<>();
        collectRows(table, page, rows);
        if (rows.isEmpty()) {
            return;
        }

        // 격자에 칸을 놓는다. RowSpan 칸은 아래 행 자리도 차지한다
        List<Cell[]> grid = new ArrayList<>();
        int columns = 0;
        for (int r = 0; r < rows.size(); r++) {
            ensureRow(grid, r, columns);
            int c = 0;
            for (Cell cell : rows.get(r)) {
                while (c < grid.get(r).length && grid.get(r)[c] != null) {
                    c++;
                }
                for (int dr = 0; dr < cell.rowSpan(); dr++) {
                    int needed = c + cell.colSpan();
                    columns = Math.max(columns, needed);
                    ensureRow(grid, r + dr, columns);
                    for (int dc = 0; dc < cell.colSpan(); dc++) {
                        grid.get(r + dr)[c + dc] = cell;
                    }
                }
                c += cell.colSpan();
            }
        }
        int width = columns;
        grid.replaceAll(row -> row.length >= width ? row : java.util.Arrays.copyOf(row, width));
        grid.subList(rows.size(), grid.size()).clear(); // RowSpan 이 표 밖으로 넘친 자리

        TableShape shape = shape(grid, width);
        if (shape == TableShape.LABEL_COLUMN) {
            addLabelColumnTable(grid, width, page);
            return;
        }
        int headerRows = shape == TableShape.NO_HEADER ? 0 : headerRows(rows, grid.size());
        List<String> names = headerRows == 0 ? List.of() : columnNames(grid, headerRows, width);
        fillMergedCellAbove(grid, headerRows, width);

        List<TableRow> tableRows = new ArrayList<>();
        for (int r = headerRows; r < grid.size(); r++) {
            List<TableField> fields = new ArrayList<>();
            int rowPage = Integer.MAX_VALUE;
            Cell previous = null;
            for (int c = 0; c < width; c++) {
                Cell cell = grid.get(r)[c];
                if (cell == null || cell == previous) {
                    continue;
                }
                previous = cell;
                if (!cell.text().isEmpty()) {
                    rowPage = Math.min(rowPage, cell.page());
                    fields.add(new TableField(names.isEmpty() ? null : names.get(c), cell.text()));
                }
            }
            if (!fields.isEmpty()) {
                // 글자가 있는 칸의 쪽. 칸이 MCID 를 가리키지 않으면 표 요소의 쪽
                tableRows.add(new TableRow(fields, rowPage == Integer.MAX_VALUE ? pageNumber(page) : rowPage));
            }
        }
        if (!tableRows.isEmpty()) {
            blocks.add(new TableBlock(names, tableRows));
        }
    }

    /** 표 모양: 첫 행이 머리 행 / 머리 행 없음 / 첫 열이 항목 이름 */
    enum TableShape {
        HEADER, NO_HEADER, LABEL_COLUMN
    }

    /**
     * 첫 행 칸이 값처럼 보이면(전화·주소·URL) 머리 행 없음.
     * 첫 행 둘째 칸부터가 목록·설명처럼 보이면(· 로 시작, 쉼표, 30자 초과) 첫 열이 항목 이름인 표.
     */
    private static TableShape shape(List<Cell[]> grid, int width) {
        if (grid.size() < 2) {
            return TableShape.HEADER;
        }
        Cell[] first = grid.getFirst();
        Cell previous = null;
        boolean descriptive = false;
        for (int c = 0; c < width; c++) {
            Cell cell = first[c];
            if (cell == null || cell == previous || cell.text().isEmpty()) {
                continue;
            }
            previous = cell;
            if (VALUE_LIKE.matcher(cell.text()).find()) {
                return TableShape.NO_HEADER;
            }
            if (c > 0 && (cell.text().startsWith("·") || cell.text().contains(",") || cell.text().length() > 30)) {
                descriptive = true;
            }
        }
        return descriptive ? TableShape.LABEL_COLUMN : TableShape.HEADER;
    }

    /** 첫 열이 항목 이름인 표: 행마다 "항목: 값 / 값 / 값" (첫 행 포함) */
    private void addLabelColumnTable(List<Cell[]> grid, int width, PDPage page) {
        List<TableRow> tableRows = new ArrayList<>();
        for (Cell[] row : grid) {
            List<TableField> fields = new ArrayList<>();
            int rowPage = Integer.MAX_VALUE;
            Cell previous = null;
            String label = null;
            for (int c = 0; c < width; c++) {
                Cell cell = row[c];
                if (cell == null || cell == previous || cell.text().isEmpty()) {
                    continue;
                }
                previous = cell;
                rowPage = Math.min(rowPage, cell.page());
                if (c == 0) {
                    label = cell.text();
                } else {
                    fields.add(new TableField(fields.isEmpty() ? label : null, cell.text()));
                }
            }
            if (fields.isEmpty() && label != null) {
                fields.add(new TableField(null, label));
            }
            if (!fields.isEmpty()) {
                tableRows.add(new TableRow(fields, rowPage == Integer.MAX_VALUE ? pageNumber(page) : rowPage));
            }
        }
        if (!tableRows.isEmpty()) {
            blocks.add(new TableBlock(List.of(), tableRows));
        }
    }

    /**
     * 위아래로 합쳐진 칸의 글자가 태그상 둘째 데이터 행에 들어 있고 첫 데이터 행에는 빈 칸(또는 칸 없음)만 있을 때,
     * 바로 아래 행 같은 열의 합쳐진 칸(RowSpan) 값을 첫 데이터 행에도 채운다.
     * RowSpan 없이 빈 칸 / 값 / 빈 칸 으로만 합친 것처럼 보이게 한 표는 값을 추정해야 하므로 채우지 않는다.
     */
    private static void fillMergedCellAbove(List<Cell[]> grid, int firstDataRow, int width) {
        if (firstDataRow + 1 >= grid.size()) {
            return;
        }
        Cell[] row = grid.get(firstDataRow);
        Cell[] below = grid.get(firstDataRow + 1);
        for (int c = 0; c < width; c++) {
            boolean emptyAbove = row[c] == null || row[c].text().isEmpty();
            if (emptyAbove && below[c] != null && below[c].rowSpan() > 1 && !below[c].text().isEmpty()) {
                row[c] = below[c];
            }
        }
    }

    private static void ensureRow(List<Cell[]> grid, int row, int columns) {
        while (grid.size() <= row) {
            grid.add(new Cell[columns]);
        }
        if (grid.get(row).length < columns) {
            grid.set(row, java.util.Arrays.copyOf(grid.get(row), columns));
        }
    }

    /** 첫 행과, 첫 행 칸이 RowSpan 으로 이어지는 행까지. 행이 하나뿐이면 머리 행 없음 */
    private static int headerRows(List<List<Cell>> rows, int total) {
        if (total < 2) {
            return 0;
        }
        int header = 1;
        for (Cell cell : rows.getFirst()) {
            header = Math.max(header, cell.rowSpan());
        }
        return Math.min(header, total - 1);
    }

    /** 열마다 머리 행 칸 글자를 위에서부터 이어 붙인다 (같은 칸은 한 번) */
    private static List<String> columnNames(List<Cell[]> grid, int headerRows, int width) {
        List<String> names = new ArrayList<>(width);
        boolean any = false;
        for (int c = 0; c < width; c++) {
            Set<Cell> seen = new LinkedHashSet<>();
            for (int r = 0; r < headerRows; r++) {
                Cell cell = grid.get(r)[c];
                if (cell != null && !cell.text().isEmpty()) {
                    seen.add(cell);
                }
            }
            String name = String.join(" ", seen.stream().map(Cell::text).toList());
            any |= !name.isEmpty();
            names.add(name.isEmpty() ? null : name);
        }
        return any ? names : List.of();
    }

    private void collectRows(PDStructureElement element, PDPage page, List<List<Cell>> rows) {
        for (Object kid : element.getKids()) {
            if (kid instanceof PDStructureElement child) {
                PDPage childPage = child.getPage() != null ? child.getPage() : page;
                if ("TR".equals(standardType(child))) {
                    List<Cell> cells = new ArrayList<>();
                    for (Object cellKid : child.getKids()) {
                        if (cellKid instanceof PDStructureElement cellElement
                                && CELL_TYPES.contains(standardType(cellElement))) {
                            cells.add(cell(cellElement,
                                    cellElement.getPage() != null ? cellElement.getPage() : childPage));
                        }
                    }
                    rows.add(cells);
                } else {
                    collectRows(child, childPage, rows); // THead / TBody / TFoot
                }
            }
        }
    }

    private Cell cell(PDStructureElement element, PDPage page) {
        List<Run> runs = new ArrayList<>();
        collectRuns(element, page, runs);
        List<TextPosition> positions = new ArrayList<>();
        runs.forEach(run -> positions.addAll(run.positions()));
        int cellPage = runs.isEmpty() ? Integer.MAX_VALUE : runs.getFirst().page();
        int rowSpan = 1;
        int colSpan = 1;
        for (int i = 0; i < element.getAttributes().size(); i++) {
            PDAttributeObject attribute = element.getAttributes().getObject(i);
            if (attribute instanceof PDTableAttributeObject table) {
                rowSpan = Math.max(1, table.getRowSpan());
                colSpan = Math.max(1, table.getColSpan());
            }
        }
        return new Cell(text(positions), cellPage, rowSpan, colSpan);
    }

    private int pageNumber(PDPage page) {
        return page == null ? 1 : pageNumbers.getOrDefault(page.getCOSObject(), 1);
    }

    // ---------------------------------------------------------------- 공통

    private static String standardType(PDStructureElement element) {
        String type = element.getStandardStructureType();
        return type == null ? "" : type;
    }

    /** MCID 를 직접 가진 요소 (P 로 매핑되지 않은 Span 등) */
    private static boolean ownsContent(PDStructureElement element) {
        for (Object kid : element.getKids()) {
            if (kid instanceof Integer || kid instanceof PDMarkedContentReference) {
                return true;
            }
        }
        return false;
    }

    /** 화면의 한 줄: 글자, 시작·끝 x, 글자 크기 */
    private record VisualLine(String text, float startX, float endX, float size) {
    }

    /** 표 칸처럼 줄바꿈 없이 한 줄로 (칸 안 줄은 공백으로 잇는다) */
    static String text(List<TextPosition> positions) {
        return String.join(" ", visualLines(positions).stream().map(VisualLine::text).toList()).strip();
    }

    /**
     * 문단을 줄로 나눈다. 자동 줄바꿈(줄이 오른쪽 끝까지 참)은 공백으로 잇고, 강제 줄바꿈은 나눈다.
     * 강제 줄바꿈: 앞 줄이 문단의 가장 긴 줄보다 글자 2개 이상 짧게 끝났거나, 다음 줄이 목록·번호 기호로 시작.
     */
    static List<String> lines(List<TextPosition> positions) {
        List<VisualLine> visual = visualLines(positions);
        float maxEnd = 0;
        for (VisualLine line : visual) {
            maxEnd = Math.max(maxEnd, line.endX());
        }
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        VisualLine previous = null;
        for (VisualLine line : visual) {
            boolean forced = previous != null
                    && (previous.endX() < maxEnd - previous.size() * 2 || LINE_START_MARKER.matcher(line.text()).find());
            if (forced && !current.isEmpty()) {
                lines.add(current.toString());
                current.setLength(0);
            }
            if (!current.isEmpty()) {
                current.append(' ');
            }
            current.append(line.text());
            previous = line;
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    /**
     * 글자를 화면 줄 단위로 모으며 띄어쓰기를 복원한다. 공백·제어 문자(이 PDF 는 공백이 U+0007)는 공백 하나,
     * 같은 줄에서 글자 사이가 공백 폭의 절반(또는 글자 크기의 1/4) 보다 넓으면 공백.
     */
    private static List<VisualLine> visualLines(List<TextPosition> positions) {
        List<VisualLine> lines = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        TextPosition previous = null;
        TextPosition lineStart = null;
        float lineEnd = 0;
        for (TextPosition position : positions) {
            String unicode = clean(position.getUnicode());
            if (unicode.isEmpty()) {
                continue;
            }
            if (previous != null && isNewLine(previous, position)) {
                addLine(lines, text, lineStart, lineEnd);
                text.setLength(0);
                lineStart = null;
            }
            if (unicode.isBlank()) {
                appendSpace(text);
            } else {
                if (previous != null && lineStart != null && needsSpace(previous, position)) {
                    appendSpace(text);
                }
                if (lineStart == null) {
                    lineStart = position;
                }
                text.append(unicode);
                lineEnd = position.getXDirAdj() + position.getWidthDirAdj();
            }
            previous = position;
        }
        addLine(lines, text, lineStart, lineEnd);
        return lines;
    }

    private static void addLine(List<VisualLine> lines, StringBuilder text, TextPosition start, float end) {
        String line = SPACES.matcher(text).replaceAll(" ").strip();
        if (!line.isEmpty() && start != null) {
            lines.add(new VisualLine(line, start.getXDirAdj(), end, size(start)));
        }
    }

    /** 공백·제어 문자만 있으면 공백 하나, 그 밖의 제어 문자는 뺀다 */
    static String clean(String unicode) {
        if (unicode == null || unicode.isEmpty()) {
            return "";
        }
        boolean blank = true;
        StringBuilder out = new StringBuilder(unicode.length());
        for (int i = 0; i < unicode.length(); i++) {
            char c = unicode.charAt(i);
            if (Character.isWhitespace(c) || Character.isISOControl(c) || Character.isSpaceChar(c)) {
                continue;
            }
            blank = false;
            out.append(c);
        }
        return blank ? " " : out.toString();
    }

    private static float size(TextPosition position) {
        return Math.max(Math.max(position.getFontSizeInPt(), position.getHeightDir()), 1f);
    }

    private static boolean isNewLine(TextPosition previous, TextPosition current) {
        return Math.abs(current.getYDirAdj() - previous.getYDirAdj()) > size(previous) * 0.5f;
    }

    private static boolean needsSpace(TextPosition previous, TextPosition current) {
        float size = size(previous);
        float gap = current.getXDirAdj() - (previous.getXDirAdj() + previous.getWidthDirAdj());
        if (gap < -size) {
            return true; // 같은 높이에서 왼쪽으로 돌아감 (다른 칸·단)
        }
        float spaceWidth = previous.getWidthOfSpace();
        if (!(spaceWidth > 0) || Float.isNaN(spaceWidth)) {
            spaceWidth = size * 0.25f;
        }
        return gap > Math.min(spaceWidth * 0.5f, size * 0.25f);
    }

    private static void appendSpace(StringBuilder text) {
        if (!text.isEmpty() && text.charAt(text.length() - 1) != ' ') {
            text.append(' ');
        }
    }
}
