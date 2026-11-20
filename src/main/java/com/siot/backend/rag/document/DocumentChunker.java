package com.siot.backend.rag.document;

import com.siot.backend.rag.document.ContentBlock.TableBlock;
import com.siot.backend.rag.document.ContentBlock.TableRow;
import com.siot.backend.rag.document.ContentBlock.TextLine;
import com.siot.backend.rag.document.ExtractedContent.Source;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ADM-04 [SPLIT]: 쪽별 텍스트를 검색용 조각으로 나눈다 (챗봇과 합의한 규칙).
 * <ul>
 *     <li>목표 {@value #TARGET}자, 최대 {@value #MAX}자 (첫 줄 제목 포함), 같은 절 안에서 앞 조각 끝 약 {@value #OVERLAP}자를 겹친다</li>
 *     <li>절 제목이 바뀌면 새 조각. 조각 첫 줄은 절 제목 ("상위 &gt; 하위"). 제목 전 내용은 문서 이름</li>
 *     <li>문단 경계에서 자르고, 한 문단이 너무 길면 문장 경계, 그래도 길면 공백에서 자른다</li>
 *     <li>제목은 짧고({@value #HEADING_MAX}자 이하) 문장부호·이어지는 말·서술("~함", "~음")로 끝나지 않으며,
 *         앞 내용이 끝난 뒤(쪽 첫 줄, 빈 줄·문장·목록 줄 뒤, 번호·강조 제목 뒤)에 오고, 다음 줄이 본문인 줄만.
 *         ■ 와 "1-4" 번호가 붙은 줄은 짧기만 하면 제목이다.
 *         같은 번호 모양이 앞뒤 줄에 이어지면 목록 항목으로 본다 ("1. 여권 / 2. 사진").
 *         목차 줄("…… 12"), 서식 칸("년/年 월/月"), 본문 속 조문 인용("제13조에 따라")은 제목이 아니다</li>
 *     <li>태그 없는 PDF(PLAIN)는 번호·기호가 있거나 글자가 본문보다 {@value #EMPHASIS_RATIO}배 이상 크거나 굵은 줄만 제목이다</li>
 *     <li>제목 단계는 문서에서 처음 나온 순서로 정한다 (처음 보는 모양은 그때 제목 경로의 바로 아래 단계).
 *         같거나 높은 단계의 제목이 나오면 그 아래 단계 제목은 모두 지운다. 글자 크기를 알면 큰 제목이 작은 제목 아래에 들지 않는다.
 *         최상위 제목(장)이 나오면 그 아래 단계를 새로 정하고, 장 제목과 같은 모양의 제목은 늘 새 장이다</li>
 *     <li>PLAIN 에서 쪽 오른쪽 끝까지 찬 줄은 제목이 아니고, 그 다음 줄도 새 블록이 아니다 (줄바꿈된 문장)</li>
 *     <li>표는 1차로 문단 기준으로 다룬다. 숫자·짧은 칸이 반복되는 줄이 이어지면 한 덩어리로 묶고 {@link Chunk#tableLike()} 로 표시한다</li>
 *     <li>태그 PDF 에서 따로 떨어진 기호·괄호 줄("※", "(", ")")은 앞뒤 줄에 붙인다</li>
 *     <li>본문이 {@value #MIN_BODY}자 미만인 조각은 같은 절의 다음(안 되면 앞) 조각과 합친다.
 *         본문이 {@value #TINY_BODY}자 미만이고 절이 바뀌는 조각(표지·간지)은 다음 절 첫 조각에 붙인다. 표 조각은 합치지 않는다</li>
 * </ul>
 */
public class DocumentChunker {

    public static final int TARGET = 600;
    public static final int MAX = 1000;
    public static final int OVERLAP = 100;
    static final int HEADING_MAX = 40;
    /** 표로 볼 연속 줄 수 */
    static final int TABLE_MIN_ROWS = 3;
    /** 본문이 이보다 짧은 조각은 이웃 조각과 합친다 */
    static final int MIN_BODY = 200;
    /** 본문이 이보다 짧고 절이 바뀌는 조각은 다음 절 첫 조각에 붙인다 */
    static final int TINY_BODY = 30;
    /** 본문 글자 크기(중앙값)의 이 배수 이상이면 강조된 줄 */
    static final double EMPHASIS_RATIO = 1.15;
    private static final int MIN_BUDGET = 200;
    private static final int TITLE_LEVELS = 3;

    private static final Pattern SENTENCE_END = Pattern.compile("[.!?。！？]$");
    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("(?<=[.!?。！？])\\s+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern HAS_LETTER = Pattern.compile("[\\p{L}]");
    private static final Pattern LETTER_OR_DIGIT = Pattern.compile("[\\p{L}\\d]");
    /** 목록 기호 (제목으로 보지 않음) */
    private static final Pattern BULLET = Pattern.compile("^[-•·※○◦▪∙*]\\s*");
    /** 다음 줄로 이어지는 문장 끝 (조사·연결 어미). 번호 없는 줄 */
    private static final Pattern CONTINUES = Pattern.compile(
            "(?:[을를은는에와과며면및로의]|[하되있없이]고|[위대관의통인당생정용능련]한|[하되있없]는|[하되]여|따른|등)$");
    /** 번호가 있는 줄도 이어지는 문장으로 보는 끝 ("1. 근로를 제공한 날부터 …를") */
    private static final Pattern CONTINUES_STRONG = Pattern.compile(
            "(?:[을를며및]|[위대관의통]한|따른|하여|에서|에게|부터)$");
    /**
     * 번호가 있는 긴 줄({@value #LONG_NUMBERED}자 이상)이 이어지는 말로 끝나면 줄바꿈된 문장
     * ("② 벌금형 300만원 미만 : 형이 확정된 시점과", "3) … 상용고용 형태로"). 짧은 번호 제목("1. 과태료 부과")은 아니다
     */
    private static final Pattern CONTINUES_LONG = Pattern.compile(
            "(?:[을를은는에와과며면및로의]|[하되있없이]고|[가-힣]한|[가-힣]는|[하되]여|따른)$");
    static final int LONG_NUMBERED = 20;
    /** 서술 끝 ("~받아야 함", "할 수 있음", "~합니다") */
    private static final Pattern PREDICATE_END = Pattern.compile(
            "(?:[가-힣]다|[가-힣]+(?:함|됨)|[있없았었였했됐겠받않같많]음|\\s(?:함|됨|임|음)|[가-힣](?<!모)임|[.다]\\s*\\d{1,3}\\))\\.?$");
    /** 서식 칸 ("년/年 월/月 일/日", "(서명 또는 날인)") */
    private static final Pattern FORM_FIELD = Pattern.compile("[年月日]|\\(\\s*(?:인|서명|날인|서명\\s*또는\\s*날인)\\s*\\)$");
    /** 목차 줄: 점선 또는 쪽 번호로 끝남 ("…… 12", "1. 유학, 연수 (D-2, D-4) 09") */
    private static final Pattern TOC_LINE = Pattern.compile("(?:\\.{3,}|…|·{3,}|ㆍ{3,}|‥|-{3,}|_{3,})\\s*\\d{0,4}\\s*$|\\S\\s+\\d{1,3}$");
    /** 목차 쪽의 제목 ("목  차", "CONTENTS"): 문서 맨 앞에서 최상위 제목이 되어 끝까지 남지 않게 */
    private static final Pattern TOC_TITLE = Pattern.compile(
            "^(?:" + PageNoiseFilter.PRIVATE_USE + "|[\\s<\\[(〈《【])*(?:목\\s*차|目\\s*次|차\\s*례|(?i:table\\s+of\\s+)?(?i:contents))[\\s>\\])〉》】]*$");
    /** 서식 끝의 기관 서명 ("법 무 부", "출입국․외국인정책본부"): 공백을 뺀 줄 전체가 기관 이름 */
    private static final Pattern AGENCY_SIGNATURE = Pattern.compile("^(?:[가-힣]{1,4}부|[가-힣·․ㆍ]{2,14}본부|[가-힣·․ㆍ]{2,12}외국인청)$");
    /** 조문 인용 ("제13조에 따라", "제28조 제1항") */
    private static final Pattern ARTICLE_START = Pattern.compile("^제\\s*\\d+\\s*조");
    /** 닫는 괄호류로 시작 (앞 줄에서 이어지는 링크 글자 ")-외국인근로자 고용관리-4대 사회보험]") */
    private static final Pattern STARTS_CLOSING = Pattern.compile("^[)\\]}」』”’>〉》】]");
    /** 장-절 번호만 있는 줄 (태그 PDF 에서 제목과 따로 놓인 번호) */
    private static final Pattern SECTION_NUMBER_ONLY = Pattern.compile("^\\d{1,2}-\\d{1,2}$");
    /** 번호 없는 제목이 될 수 없는 줄: "항목 : 값", 괄호로 시작, 주소 */
    private static final Pattern NOT_A_TITLE = Pattern.compile("\\s:\\s|^[(\\[]|www\\.|https?://");
    /** 다음 줄로 이어지는 목록 줄 끝 (쉼표, 가운뎃점, 여는 괄호, 콜론, 줄표) */
    private static final Pattern LIST_CONTINUES = Pattern.compile("[,·(:\\-–]\\s*$");
    private static final String OPENING = "([{「『“‘<〈《【";
    private static final String CLOSING = ")]}」』”’>〉》】";
    private static final String PRIVATE_USE = PageNoiseFilter.PRIVATE_USE;

    /**
     * 번호·기호 모양. 제목 단계는 문서에서 처음 나온 순서로 정한다 ({@link #toSections}).
     * strong 인 모양(■, "1-4")은 짧기만 하면 앞뒤 줄과 상관없이 제목으로 본다.
     */
    enum Marker {
        CHAPTER("^(?:제\\s*\\d+\\s*[장편부]|PART\\s*\\d+)", false),
        /** "1-4 지방세 납부" (장-절 번호) */
        SECTION_NUM("^\\d{1,2}-\\d{1,2}\\s+", true),
        SECTION("^제\\s*\\d+\\s*절", false),
        /** "제13조(연수생 관리 부실업체의 연수제한)": 줄 맨 앞의 괄호 제목 꼴만. "제13조에 따라 …" 는 아니다 */
        ARTICLE("^제\\s*\\d+\\s*조(?:\\s*의\\s*\\d+)?\\s*\\([^)]{1,40}\\)", false),
        ROMAN("^(?:[ⅠⅡⅢⅣⅤⅥⅦⅧⅨⅩ]|[IVX]{1,4}\\.)\\s*", false),
        BRACKET("^【[^】]{1,30}】", false),
        SQUARE("^■\\s*", true),
        SYMBOL("^(?:[□▶▷●◆◇◎▣❶-❿➊-➓]|" + PRIVATE_USE + ")\\s*", false),
        NUM_DOT("^\\d{1,2}\\.\\s+", false),
        HANGUL_DOT("^[가-하]\\.\\s*", false),
        NUM_PAREN("^\\d{1,2}\\)\\s*", false),
        PAREN_NUM("^\\(\\d{1,2}\\)\\s*", false),
        CIRCLED("^[①-⑳]\\s*", false),
        NONE(null, false);

        final Pattern pattern;
        final boolean strong;

        Marker(String regex, boolean strong) {
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

        /** 제목 줄을 뺀 본문 */
        public String body() {
            return title == null ? content : content.substring(Math.min(content.length(), title.length() + 1));
        }
    }

    /**
     * @param pageStart  쪽의 첫 줄 (앞 줄과 쪽이 다름)
     * @param table      숫자 칸이 많은 줄 (표의 행)
     * @param header     짧은 칸만 여러 개인 줄. 바로 아래가 표의 행이면 표의 머리 행
     * @param tableBlock 태그 PDF 의 표 자리 (text 는 빈 문자열)
     * @param fontSize   글자 크기 (0 이면 모름)
     * @param emphasized 본문보다 확실히 크거나 굵음 (PLAIN 에서만)
     * @param fullWidth  쪽의 오른쪽 끝까지 찬 줄 (PLAIN 에서만, 문장이 다음 줄로 이어짐)
     */
    private record Line(String text, int page, boolean blankBefore, boolean pageStart, Marker marker,
                        boolean table, boolean header, TableBlock tableBlock, float fontSize, boolean bold,
                        boolean emphasized, boolean fullWidth) {
    }

    /** 제목을 고르는 방식 */
    private record Rules(boolean strict) {
        /** 출처 모름 (쪽 텍스트만 받은 경우): 번호·강조 없는 제목도 허용 */
        static final Rules LENIENT = new Rules(false);
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
     * 내보내기 전 조각.
     *
     * @param overlap 첫 단위가 앞 조각 끝을 겹친 것 (합칠 때 뺀다)
     */
    private record Draft(String title, List<Unit> units, boolean structuredTable, boolean overlap) {

        int bodyLength() {
            int length = -1;
            for (Unit unit : units) {
                length += unit.text().length() + 1;
            }
            return Math.max(0, length);
        }

        int contentLength() {
            return (title == null ? 0 : title.length() + 1) + bodyLength();
        }

        List<Unit> withoutOverlap() {
            return overlap && units.size() > 1 ? units.subList(1, units.size()) : units;
        }
    }

    /**
     * 태그 없는 PDF 의 쪽 텍스트 (줄 단위). 출처를 모르므로 번호·강조 없는 제목도 허용한다.
     *
     * @param fallbackTitle 첫 제목 전 내용의 첫 줄 (보통 문서 이름). null 이면 제목 줄 없음
     */
    public List<Chunk> split(List<PageText> pages, String fallbackTitle) {
        return splitBlocks(PdfContentExtractor.toBlocks(pages), fallbackTitle);
    }

    public List<Chunk> split(ExtractedContent content, String fallbackTitle) {
        return splitBlocks(content.blocks(), fallbackTitle, content.source());
    }

    /**
     * @param blocks        읽는 순서대로의 텍스트 줄·표
     * @param fallbackTitle 첫 제목 전 내용의 첫 줄 (보통 문서 이름). null 이면 제목 줄 없음
     */
    public List<Chunk> splitBlocks(List<ContentBlock> blocks, String fallbackTitle) {
        return splitBlocks(blocks, fallbackTitle, null);
    }

    /**
     * @param source PLAIN 이면 번호·강조 있는 줄만 제목, TAGGED 면 따로 떨어진 기호·괄호 줄을 붙인다. null 이면 출처 모름
     */
    public List<Chunk> splitBlocks(List<ContentBlock> blocks, String fallbackTitle, Source source) {
        List<ContentBlock> prepared = source == Source.TAGGED ? joinFragments(blocks) : blocks;
        Rules rules = source == Source.PLAIN ? new Rules(true) : Rules.LENIENT;
        List<Line> lines = toLines(prepared, source == Source.PLAIN);
        boolean[] headings = findHeadings(lines, rules);
        List<Section> sections = toSections(lines, headings, fallbackTitle);

        List<Draft> drafts = new ArrayList<>();
        for (Section section : sections) {
            pack(section, drafts);
        }
        List<Chunk> chunks = new ArrayList<>();
        for (Draft draft : mergeShort(drafts)) {
            emit(draft, chunks);
        }
        return chunks;
    }

    // ---------------------------------------------------------------- 줄

    /**
     * 태그 PDF 에서 링크·괄호 때문에 따로 떨어진 줄을 붙인다 (같은 쪽 안에서만).
     * <ul>
     *     <li>기호만 있거나 {@value #FRAGMENT_MAX}자 이하인 줄: 닫는 괄호류는 앞 줄 끝에, 그 밖("※", "(")은 다음 줄 앞에</li>
     *     <li>닫는 괄호류로 시작하는 줄, 여는 괄호류로 끝난 줄의 다음 줄은 앞 줄에 잇는다</li>
     * </ul>
     */
    static List<ContentBlock> joinFragments(List<ContentBlock> blocks) {
        List<ContentBlock> joined = new ArrayList<>(blocks.size());
        TextLine pending = null;
        for (ContentBlock block : blocks) {
            if (!(block instanceof TextLine line)) {
                if (pending != null) {
                    appendOrAdd(joined, pending);
                    pending = null;
                }
                joined.add(block);
                continue;
            }
            String text = line.text().strip();
            if (text.isEmpty()) {
                continue;
            }
            if (pending != null) {
                if (pending.page() == line.page()) {
                    line = new TextLine(join(pending.text(), text), line.page(), pending.newParagraph(),
                            line.fontSize(), line.bold(), line.fullWidth());
                    text = line.text();
                } else {
                    appendOrAdd(joined, pending);
                }
                pending = null;
            }
            TextLine previous = joined.isEmpty() || !(joined.getLast() instanceof TextLine last)
                    || last.page() != line.page() ? null : last;
            boolean fragment = isFragment(text);
            if (previous != null && (STARTS_CLOSING.matcher(text).find() || endsWithOpening(previous.text()))) {
                joined.set(joined.size() - 1, withText(previous, previous.text() + text));
            } else if (fragment) {
                pending = line;
            } else {
                joined.add(line);
            }
        }
        if (pending != null) {
            appendOrAdd(joined, pending);
        }
        return joined;
    }

    static final int FRAGMENT_MAX = 3;

    private static boolean isFragment(String text) {
        return text.length() <= FRAGMENT_MAX || !LETTER_OR_DIGIT.matcher(text).find();
    }

    private static boolean endsWithOpening(String text) {
        return !text.isEmpty() && OPENING.indexOf(text.charAt(text.length() - 1)) >= 0;
    }

    /** 다음 줄이 없어서 붙일 곳이 없는 조각: 같은 쪽 앞 줄에 붙이고, 그것도 없으면 그대로 둔다 */
    private static void appendOrAdd(List<ContentBlock> joined, TextLine fragment) {
        if (!joined.isEmpty() && joined.getLast() instanceof TextLine last && last.page() == fragment.page()) {
            String separator = CLOSING.indexOf(fragment.text().charAt(0)) >= 0 || endsWithOpening(last.text()) ? "" : " ";
            joined.set(joined.size() - 1, withText(last, last.text() + separator + fragment.text()));
        } else {
            joined.add(fragment);
        }
    }

    private static String join(String fragment, String next) {
        return endsWithOpening(fragment) ? fragment + next : fragment + " " + next;
    }

    private static TextLine withText(TextLine line, String text) {
        return new TextLine(text, line.page(), line.newParagraph(), line.fontSize(), line.bold(), line.fullWidth());
    }

    /** 표는 빈 줄(text "")로 자리만 잡는다: 제목 판단에서 본문으로 보이고, 절에 표로 들어간다 */
    private static List<Line> toLines(List<ContentBlock> blocks, boolean useEmphasis) {
        BodyStyle body = useEmphasis ? BodyStyle.of(blocks) : BodyStyle.UNKNOWN;
        List<Line> lines = new ArrayList<>();
        int previousPage = -1;
        for (ContentBlock block : blocks) {
            switch (block) {
                case TextLine textLine -> {
                    // 겹친 공백은 하나로 ("미성년  자녀": 제목 길이 판단이 공백 수에 흔들리지 않게)
                    String text = WHITESPACE.matcher(textLine.text().replace(' ', ' ')).replaceAll(" ").strip();
                    if (text.isEmpty()) {
                        continue;
                    }
                    Marker marker = Marker.of(text);
                    lines.add(new Line(text, textLine.page(), textLine.newParagraph(), textLine.page() != previousPage,
                            marker, isTableRow(text), marker == Marker.NONE && isHeaderRow(text), null,
                            textLine.fontSize(), textLine.bold(), body.emphasized(textLine), textLine.fullWidth()));
                    previousPage = textLine.page();
                }
                case TableBlock table -> {
                    if (table.rows().isEmpty()) {
                        continue;
                    }
                    int page = table.firstPage();
                    lines.add(new Line("", page, true, page != previousPage, Marker.NONE, false, false, table,
                            0, false, false, false));
                    previousPage = table.rows().getLast().page();
                }
            }
        }
        return mergeSectionNumbers(lines);
    }

    /** 본문 글자 모양: 글자 수 가중 중앙값 크기, 굵은 글자 비율 */
    private record BodyStyle(float medianSize, boolean mostlyBold) {
        static final BodyStyle UNKNOWN = new BodyStyle(0, true);

        static BodyStyle of(List<ContentBlock> blocks) {
            List<float[]> sizes = new ArrayList<>();
            long total = 0;
            long bold = 0;
            for (ContentBlock block : blocks) {
                if (block instanceof TextLine line && line.fontSize() > 0) {
                    int length = line.text().length();
                    sizes.add(new float[]{line.fontSize(), length});
                    total += length;
                    if (line.bold()) {
                        bold += length;
                    }
                }
            }
            if (total == 0) {
                return UNKNOWN;
            }
            sizes.sort((a, b) -> Float.compare(a[0], b[0]));
            long seen = 0;
            float median = sizes.getLast()[0];
            for (float[] size : sizes) {
                seen += (long) size[1];
                if (seen * 2 >= total) {
                    median = size[0];
                    break;
                }
            }
            return new BodyStyle(median, bold * 2 >= total);
        }

        boolean emphasized(TextLine line) {
            if (medianSize <= 0 || line.fontSize() <= 0) {
                return false;
            }
            return line.fontSize() >= medianSize * EMPHASIS_RATIO || (line.bold() && !mostlyBold);
        }
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
                        next.marker(), false, false, null, next.fontSize(), next.bold(), next.emphasized(),
                        next.fullWidth()),
                        line.text() + " " + next.text()));
                i++;
            } else {
                merged.add(line);
            }
        }
        return merged;
    }

    private static boolean isPlainTitle(Line line, int page) {
        return line != null && line.page() == page && line.tableBlock() == null
                && line.marker() == Marker.NONE && isHeadingCandidate(line, Rules.LENIENT);
    }

    private static Line withText(Line line, String text) {
        Marker marker = Marker.of(text);
        return new Line(text, line.page(), line.blankBefore(), line.pageStart(), marker, false, false, null,
                line.fontSize(), line.bold(), line.emphasized(), line.fullWidth());
    }

    /**
     * 칸이 3개 이상이고 그중 40% 이상이 숫자 칸인 줄 (예: "외국인등록 30,000원 3주").
     * 숫자 칸은 글자의 절반 이상이 숫자인 칸. 줄 앞의 번호("13.")는 칸으로 세지 않는다
     * ("13. 영주권자의 국내 출생자녀〔F-5-20〕" 은 표가 아니다).
     */
    static boolean isTableRow(String line) {
        if (SENTENCE_END.matcher(line).find()) {
            return false;
        }
        String[] cells = WHITESPACE.split(line);
        int from = Marker.of(line) != Marker.NONE ? 1 : 0;
        if (cells.length - from < 3) {
            return false;
        }
        int digitCells = 0;
        for (int i = from; i < cells.length; i++) {
            if (isNumericCell(cells[i])) {
                digitCells++;
            }
        }
        return digitCells * 10 >= (cells.length - from) * 4;
    }

    private static boolean isNumericCell(String cell) {
        int digits = 0;
        for (int i = 0; i < cell.length(); i++) {
            if (Character.isDigit(cell.charAt(i))) {
                digits++;
            }
        }
        return digits > 0 && digits * 2 >= cell.length() - 1;
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

    private static boolean[] findHeadings(List<Line> lines, Rules rules) {
        int n = lines.size();
        boolean[] candidate = new boolean[n];
        for (int i = 0; i < n; i++) {
            candidate[i] = isHeadingCandidate(lines.get(i), rules) && !isListItem(lines, i);
        }
        // 앞에서부터: 제목은 앞 내용이 끝난 뒤, 또는 제목이 될 수 있는 줄 바로 뒤에 온다
        boolean[] canStart = new boolean[n];
        for (int i = 0; i < n; i++) {
            canStart[i] = startsBlock(lines, i, rules)
                    || (i > 0 && candidate[i - 1] && (canStart[i - 1] || lines.get(i - 1).marker().strong)
                    && followsHeading(lines.get(i - 1), lines.get(i)));
        }
        boolean[] heading = new boolean[n];
        // 뒤에서부터: "2. 신청 / 가. 기한 / 본문" 처럼 제목이 이어지면 다음 줄이 제목이어도 된다
        for (int i = n - 1; i >= 0; i--) {
            Line line = lines.get(i);
            if (i + 1 >= n || !candidate[i]) {
                continue;
            }
            // ■ 와 "1-4" 는 짧기만 하면 제목
            if (line.marker().strong) {
                heading[i] = true;
                continue;
            }
            if (!canStart[i]) {
                continue;
            }
            Line next = lines.get(i + 1);
            if (line.header() && next.table()) {
                continue;
            }
            boolean nextIsBody = !isHeadingCandidate(next, rules);
            boolean nextIsSubHeading = heading[i + 1] && !sameShape(next, line);
            // 번호·기호가 있거나 강조된 줄은 다음 줄이 제목으로 정해지지 않았으면 제목
            // ("Ⅲ. 연말정산 계산사례 / 사례 1. … / 기본사항": 다음 줄들이 크게 쓰였어도 Ⅲ 은 제목).
            // 번호 없는 짧은 줄이 이어지는 것("여권 / 사진")은 아니다
            boolean marked = line.marker() != Marker.NONE || line.emphasized();
            heading[i] = nextIsBody || nextIsSubHeading || (marked && !heading[i + 1]);
        }
        return heading;
    }

    /**
     * 제목 바로 뒤의 제목 ("Ⅱ. 체류자격별 안내 / 1. 외교(A-1)", "2 여권 유효기간 … 안내 / ▣ 적용대상"):
     * 두 줄 모두 번호·기호가 있거나 강조되어 있고 모양이 다르다. 번호 없는 짧은 줄이 이어지는 것("여권 / 사진")은 아니다.
     */
    private static boolean followsHeading(Line previous, Line line) {
        return (previous.marker() != Marker.NONE || previous.emphasized())
                && (line.marker() != Marker.NONE || line.emphasized())
                && !sameShape(previous, line);
    }

    private static boolean sameShape(Line a, Line b) {
        return shapeKey(a).equals(shapeKey(b));
    }

    /** 제목 모양: 번호·기호 종류, 번호 없으면 글자 크기·굵기 */
    private static String shapeKey(Line line) {
        if (line.marker() != Marker.NONE) {
            return line.marker().name();
        }
        return "NONE:" + Math.round(line.fontSize()) + (line.bold() ? "B" : "");
    }

    private static boolean isHeadingCandidate(Line line, Rules rules) {
        String text = line.text();
        boolean numbered = line.marker() != Marker.NONE;
        return isPlausibleTitle(text, line.marker())
                && !line.table()
                // 번호 없는 줄이 숫자 위주면 통계·값 ("514,331명", "101개교")
                && !(!numbered && mostlyDigits(text))
                // 번호 없는 줄이 "항목 : 값", 괄호 설명, 주소(URL)면 제목이 아니다
                && !(!numbered && NOT_A_TITLE.matcher(text).find())
                // PLAIN: 번호·기호가 있거나, 본문보다 크거나 굵은 줄만
                && (!rules.strict() || numbered || line.emphasized())
                // PLAIN: 쪽 오른쪽 끝까지 찬 줄은 다음 줄로 이어지는 문장 ("④ … 어느 하나에 해")
                && !(rules.strict() && line.fullWidth());
    }

    /**
     * 글자만 보고 제목이 될 수 있는지 (글자 모양·앞뒤 줄은 보지 않음). 확인 리포트의 "제목 붙음" 지표에도 쓴다.
     */
    public static boolean isPlausibleTitle(String title) {
        return title != null && isPlausibleTitle(title.strip(), Marker.of(title.strip()));
    }

    private static boolean isPlausibleTitle(String text, Marker marker) {
        boolean numbered = marker != Marker.NONE;
        return text.length() <= HEADING_MAX
                && !SENTENCE_END.matcher(text).find()
                && !text.endsWith(":") && !text.endsWith(",")
                && !BULLET.matcher(text).find()
                && HAS_LETTER.matcher(text).find()
                && !STARTS_CLOSING.matcher(text).find()
                // 문장이 다음 줄로 이어짐 ("… 국내에서 최초로", "… 달까지 발생한")
                && !(numbered ? CONTINUES_STRONG : CONTINUES).matcher(text).find()
                && !(numbered && text.length() >= LONG_NUMBERED && CONTINUES_LONG.matcher(text).find())
                // 서술 끝 ("입국허가를 받아야 함", "할 수 있음")
                && !PREDICATE_END.matcher(text).find()
                && !FORM_FIELD.matcher(text).find()
                // 목차 줄은 제목도 아니고 단계 계산에도 넣지 않는다 (PART 번호는 예외)
                && !(marker != Marker.CHAPTER && TOC_LINE.matcher(text).find())
                && !TOC_TITLE.matcher(text).find()
                && !(marker == Marker.NONE && AGENCY_SIGNATURE.matcher(text.replace(" ", "")).matches())
                // 본문 속 조문 인용 ("제13조에 따라 …")
                && !(marker != Marker.ARTICLE && ARTICLE_START.matcher(text).find());
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

    private static boolean isTocLine(Line line) {
        return line.marker() != Marker.CHAPTER && TOC_LINE.matcher(line.text()).find();
    }

    /** 앞이나 뒤 줄이 같은 번호 모양이면 목록 항목 */
    private static boolean isListItem(List<Line> lines, int i) {
        Marker marker = lines.get(i).marker();
        if (marker == Marker.NONE || marker == Marker.CHAPTER || marker == Marker.SECTION || marker == Marker.ARTICLE) {
            return false;
        }
        // 목차 줄("Ⅰ. 외국인등록 …… 3")은 바로 뒤의 같은 번호 제목을 목록으로 만들지 않는다
        boolean prevSame = i > 0 && lines.get(i - 1).marker() == marker && !isTocLine(lines.get(i - 1));
        boolean nextSame = i + 1 < lines.size() && lines.get(i + 1).marker() == marker && !isTocLine(lines.get(i + 1));
        return prevSame || nextSame;
    }

    /**
     * 제목은 앞 내용이 끝난 뒤에 온다 (문서 처음, 쪽 첫 줄, 빈 줄 뒤, 문장·서술 끝 뒤, 목록 줄 뒤).
     * 앞 줄이 이어지는 말("~로", "~한")로 끝나면 문장 중간이다.
     */
    private static boolean startsBlock(List<Line> lines, int i, Rules rules) {
        Line line = lines.get(i);
        if (i == 0) {
            return true;
        }
        Line prev = lines.get(i - 1);
        // PLAIN: 앞 줄이 오른쪽 끝까지 차고 문장이 끝나지 않았으면 이어지는 줄 (쪽이 바뀌어도)
        // 단, 새 쪽 첫 줄이 강조된 줄이면 장 제목 ("… 장 귀하" 로 끝난 서식 쪽 다음의 "기술연수(D-3)")
        if (rules.strict() && prev.fullWidth() && !SENTENCE_END.matcher(prev.text()).find()
                && !PREDICATE_END.matcher(prev.text()).find() && !(line.pageStart() && line.emphasized())) {
            return false;
        }
        if (line.pageStart()) {
            return true;
        }
        if (prev.tableBlock() != null) {
            return true;
        }
        if (line.blankBefore()) {
            return true;
        }
        String previous = prev.text();
        if (SENTENCE_END.matcher(previous).find() || PREDICATE_END.matcher(previous).find()) {
            return true;
        }
        // "○ … 소득금액증명 등" 처럼 "등" 으로 끝난 목록 줄은 끝난 것으로 본다
        if ((CONTINUES.matcher(previous).find() && !previous.endsWith("등"))
                || LIST_CONTINUES.matcher(previous).find()) {
            return false;
        }
        // "· 문의 : … ☎(031)310-2089" 처럼 마침표 없이 끝나는 목록 줄 다음
        return BULLET.matcher(previous).find();
    }

    // ---------------------------------------------------------------- 절·문단

    /**
     * 제목 단계는 문서에서 처음 나온 순서로 정한다: 처음 보는 모양은 그때 제목 경로의 마지막 제목 바로 아래 단계,
     * 이미 본 모양은 그 단계. 같거나 높은 단계의 제목이 나오면 그 아래 단계 제목은 지운다.
     */
    private static List<Section> toSections(List<Line> lines, boolean[] headings, String fallbackTitle) {
        List<Section> sections = new ArrayList<>();
        Deque<Line> path = new ArrayDeque<>();
        Map<String, Integer> levels = new HashMap<>();
        Section section = new Section(fallbackTitle, new ArrayList<>());
        sections.add(section);
        Paragraph paragraph = null;
        Line prev = null;

        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            if (headings[i]) {
                // 글자가 더 큰 제목은 더 작은 제목 아래에 들지 않는다 ("방문동거(F-1)" 20pt 아래의 "영 주(F-5)" 24pt)
                while (!path.isEmpty() && isSmaller(path.peekLast(), line)) {
                    path.removeLast();
                }
                // 장 제목과 같은 모양이면 새 장 (붙임 서식의 큰 글자 "학교 변경 동의서" 아래로 "기술연수(D-3)" 가 들지 않게)
                if (Integer.valueOf(1).equals(levels.get(shapeKey(line)))) {
                    path.clear();
                }
                int parentLevel = path.isEmpty() ? 0 : levels.get(shapeKey(path.peekLast()));
                int level = levels.computeIfAbsent(shapeKey(line), key -> parentLevel + 1);
                while (!path.isEmpty() && levels.get(shapeKey(path.peekLast())) >= level) {
                    path.removeLast();
                }
                if (path.isEmpty()) {
                    // 최상위 제목(장)마다 그 아래 단계를 새로 정한다: 장마다 번호 체계가 달라도 앞 장의 단계가 남지 않게
                    levels.clear();
                    levels.put(shapeKey(line), 1);
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

    /** 글자 크기를 둘 다 알고, a 가 b 보다 확실히({@value #LARGER_RATIO}배 이상) 작다. 13pt 와 12pt 는 같은 크기로 본다 */
    private static boolean isSmaller(Line a, Line b) {
        return a.fontSize() > 0 && b.fontSize() > 0 && a.fontSize() * LARGER_RATIO <= b.fontSize();
    }

    /** 제목 단계를 글자 크기로 가를 때 "더 크다" 로 볼 배율 */
    static final float LARGER_RATIO = 1.1f;

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
    private static void pack(Section section, List<Draft> drafts) {
        String title = section.title();
        int titleLength = title == null ? 0 : title.length() + 1;
        int budget = Math.max(MIN_BUDGET, MAX - titleLength);
        int target = Math.max(MIN_BUDGET / 2, TARGET - titleLength);

        List<Unit> units = new ArrayList<>();
        for (Object item : section.items()) {
            if (item instanceof Paragraph paragraph) {
                units.addAll(toUnits(paragraph, budget));
            } else if (item instanceof TableBlock table) {
                packText(units, title, budget, target, drafts);
                units.clear();
                packTable(table, title, budget, drafts);
            }
        }
        packText(units, title, budget, target, drafts);
    }

    /**
     * 표 하나를 한 조각으로 (한 줄에 한 행, 행마다 열 이름). 길면 행 경계에서 나눈다.
     * 행마다 열 이름이 붙어 있어 앞 조각 없이도 읽히므로 겹치지 않는다.
     */
    private static void packTable(TableBlock table, String title, int budget, List<Draft> drafts) {
        List<Unit> current = new ArrayList<>();
        for (TableRow row : table.rows()) {
            Paragraph rowText = new Paragraph(true);
            rowText.add(row.render(), row.page());
            for (Unit unit : toUnits(rowText, budget)) {
                if (!current.isEmpty() && length(current) + 1 + unit.text().length() > budget) {
                    drafts.add(new Draft(title, current, true, false));
                    current = new ArrayList<>();
                }
                current.add(unit);
            }
        }
        if (!current.isEmpty()) {
            drafts.add(new Draft(title, current, true, false));
        }
    }

    private static void packText(List<Unit> units, String title, int budget, int target, List<Draft> drafts) {
        List<Unit> current = new ArrayList<>();
        boolean overlap = false;
        Unit carry = null;
        for (Unit unit : units) {
            if (current.isEmpty()) {
                overlap = carry != null && carry.text().length() + 1 + unit.text().length() <= budget;
                if (overlap) {
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
                drafts.add(new Draft(title, current, false, overlap));
                current = new ArrayList<>();
                overlap = carry != null && carry.text().length() + 1 + unit.text().length() <= budget;
                if (overlap) {
                    current.add(carry);
                }
                current.add(unit);
            }
        }
        if (!current.isEmpty()) {
            drafts.add(new Draft(title, current, false, overlap));
        }
    }

    /**
     * 짧은 조각을 합친다 (표 조각은 그대로).
     * <ul>
     *     <li>본문이 {@value #TINY_BODY}자 미만이고 문장으로 끝나지 않으며 다음 조각이 다른 절이면 다음 절 첫 조각 앞에 붙인다 (표지·간지)</li>
     *     <li>본문이 {@value #MIN_BODY}자 미만이면 같은 절의 다음 조각과, 합쳐서 {@value #MAX}자를 넘으면 앞 조각과 합친다.
     *         합칠 때 뒤 조각 앞의 겹침은 뺀다</li>
     * </ul>
     */
    private static List<Draft> mergeShort(List<Draft> drafts) {
        List<Draft> queue = new ArrayList<>(drafts);
        List<Draft> merged = new ArrayList<>(drafts.size());
        for (int i = 0; i < queue.size(); i++) {
            Draft draft = queue.get(i);
            Draft next = i + 1 < queue.size() ? queue.get(i + 1) : null;
            if (!draft.structuredTable()) {
                int body = draft.bodyLength();
                if (body < TINY_BODY && next != null && !next.structuredTable()
                        && !Objects.equals(next.title(), draft.title()) && !endsSentence(draft)) {
                    Draft combined = new Draft(next.title(), concat(draft.units(), next.units()), false, false);
                    if (combined.contentLength() <= MAX) {
                        queue.set(i + 1, combined);
                        continue;
                    }
                }
                if (body < MIN_BODY) {
                    if (next != null && !next.structuredTable() && Objects.equals(next.title(), draft.title())) {
                        Draft combined = new Draft(draft.title(), concat(draft.units(), next.withoutOverlap()), false,
                                draft.overlap());
                        if (combined.contentLength() <= MAX) {
                            queue.set(i + 1, combined);
                            continue;
                        }
                    }
                    Draft previous = merged.isEmpty() ? null : merged.getLast();
                    if (previous != null && !previous.structuredTable()
                            && Objects.equals(previous.title(), draft.title())) {
                        Draft combined = new Draft(previous.title(), concat(previous.units(), draft.withoutOverlap()),
                                false, previous.overlap());
                        if (combined.contentLength() <= MAX) {
                            merged.set(merged.size() - 1, combined);
                            continue;
                        }
                    }
                }
            }
            merged.add(draft);
        }
        return merged;
    }

    /** 본문이 완결된 문장으로 끝남 (짧아도 제 절의 내용이므로 다음 절로 옮기지 않는다) */
    private static boolean endsSentence(Draft draft) {
        String last = draft.units().getLast().text().strip();
        return SENTENCE_END.matcher(last).find() || PREDICATE_END.matcher(last).find() || last.endsWith("요");
    }

    private static List<Unit> concat(List<Unit> first, List<Unit> second) {
        List<Unit> units = new ArrayList<>(first.size() + second.size());
        units.addAll(first);
        units.addAll(second);
        return units;
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

    private static void emit(Draft draft, List<Chunk> chunks) {
        StringBuilder body = new StringBuilder();
        int pageStart = Integer.MAX_VALUE;
        int pageEnd = 0;
        boolean table = false;
        for (Unit unit : draft.units()) {
            if (!body.isEmpty()) {
                body.append('\n');
            }
            body.append(unit.text());
            pageStart = Math.min(pageStart, unit.pageStart());
            pageEnd = Math.max(pageEnd, unit.pageEnd());
            table |= unit.table();
        }
        String title = draft.title();
        String content = title == null ? body.toString() : title + "\n" + body;
        chunks.add(new Chunk(chunks.size() + 1, pageStart, pageEnd, content, title, table, draft.structuredTable()));
    }
}
