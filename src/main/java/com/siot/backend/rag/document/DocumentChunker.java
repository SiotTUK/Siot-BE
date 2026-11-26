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
 *     <li>PLAIN 에서 앞 줄보다 {@value #LARGER_RATIO}배 이상 큰 "Ⅲ"·"5." 제목은 앞 내용이 끝나지 않았어도 제목이다</li>
 *     <li>05 체류 매뉴얼 구조에 맞춘 규칙: 뒤에 묶인 지침의 표제({@link BundledDocuments}), 시간제취업 칸({@link PartTimeRow})</li>
 *     <li>PLAIN 에서 쪽 오른쪽 끝까지 찬 줄은 제목이 아니고, 그 다음 줄도 새 블록이 아니다 (줄바꿈된 문장)</li>
 *     <li>표는 1차로 문단 기준으로 다룬다. 숫자·짧은 칸이 반복되는 줄이 이어지면 한 덩어리로 묶고 {@link Chunk#tableLike()} 로 표시한다</li>
 *     <li>태그 PDF 에서 따로 떨어진 기호·괄호 줄("※", "(", ")")은 앞뒤 줄에 붙인다</li>
 *     <li>본문이 {@value #MIN_BODY}자 미만인 조각은 같은 절의 다음(안 되면 앞) 조각과 합친다.
 *         본문이 {@value #TINY_BODY}자 미만이고 절이 바뀌는 조각(표지·간지)은 다음 절 첫 조각에 붙인다. 표 조각은 합치지 않는다</li>
 *     <li>바로 위 부모 제목이 같은 이웃 형제 절은 목표 길이까지 한 조각으로 묶고 하위 제목은 본문 줄로 남긴다
 *         (부모가 최상위 제목뿐이면 묶지 않고, 묶은 조각은 {@value #MERGE_MAX_PAGES}쪽 안). {@link #mergeSiblings}</li>
 *     <li>강조 기준 크기는 문서와 쪽의 본문 크기 중 큰 쪽 (별표 쪽처럼 쪽 전체 글자가 크면 표 칸이 제목이 되지 않게).
 *         쪽 끝까지 찬 줄이라도 본문보다 {@value #BOX_TITLE_RATIO}배 이상 크면 상자 제목</li>
 *     <li>"[별표 N] &lt;개정 …&gt;" 은 개정 표기를 떼고 아래 제목 줄과 이어 제목으로, "3.1.3." 점 번호는 숫자 개수로 단계를 가른다</li>
 *     <li>조각이 "※ …" 같은 짧은 머리 문장으로 끝나면 다음 조각으로 넘긴다</li>
 *     <li>PLAIN 의 05 체류관리 표는 "칸 이름: 값" 행으로 바꾸고 자격 제목 아래 "체류관리" 절에 둔다 ({@link StayTable})</li>
 * </ul>
 */
public class DocumentChunker {

    public static final int TARGET = 600;
    public static final int MAX = 1000;
    public static final int OVERLAP = 100;
    static final int HEADING_MAX = 40;
    /** "[별표 1] 재외국민 및 외국인 지역가입자의 제출서류(제4조제4항 관련)" 처럼 별표 제목은 길다 */
    static final int APPENDIX_HEADING_MAX = 60;
    /** 조각 첫 줄 제목 경로의 최대 길이. 넘으면 위 단계부터 뺀다 */
    static final int TITLE_MAX = 80;
    /** 묶음 문서 안의 제목 경로 최대 길이. 넘으면 아래 단계부터 빼서 표제·절·지역을 남긴다 */
    static final int BUNDLED_TITLE_MAX = 100;
    /** 형제 절을 묶은 조각의 최대 쪽 수 (출처 쪽 범위가 넓어지지 않게) */
    static final int MERGE_MAX_PAGES = 2;
    /** 이어지는 머리 문장("※ …", "… 다음과 같다:")으로 보는 최대 길이 */
    static final int LEAD_IN_MAX = 60;
    /** 쪽마다 본문 크기를 따로 볼 최소 글자 수 (별표 쪽처럼 쪽 전체 글자 배율이 다른 경우) */
    static final int PAGE_STYLE_MIN_CHARS = 200;
    /** 쪽 오른쪽 끝까지 찬 줄이라도 본문보다 이 배수 이상 크면 상자 제목 ("코로나19 관련 … 알림") */
    static final double BOX_TITLE_RATIO = 1.25;
    /** 표로 볼 연속 줄 수 */
    static final int TABLE_MIN_ROWS = 3;
    /** 본문이 이보다 짧은 조각은 이웃 조각과 합친다 */
    static final int MIN_BODY = 200;
    /** 본문이 이보다 짧고 절이 바뀌는 조각은 다음 절 첫 조각에 붙인다 */
    static final int TINY_BODY = 30;
    /** 본문 글자 크기(중앙값)의 이 배수 이상이면 강조된 줄 */
    static final double EMPHASIS_RATIO = 1.15;
    /** 강조된 줄은 그 쪽 본문(중앙값)보다도 이 배수 이상 크다 */
    static final double PAGE_EMPHASIS_RATIO = 1.05;
    private static final int MIN_BUDGET = 200;
    private static final int TITLE_LEVELS = 3;
    /** 묶음 문서 제목에서 늘 남기는 위 단계 수 (표제, 절, 지역) */
    private static final int BUNDLED_TOP_LEVELS = 3;

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
    private static final Pattern AGENCY_SIGNATURE = Pattern.compile("^(?:[가-힣]{1,4}(?<!납)부|[가-힣·․ㆍ]{2,14}본부|[가-힣·․ㆍ]{2,12}외국인청)$");
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
        /** "[별표 2] 지역보험료 산정 기준(제6조제1항 관련)", "[별지 제10호서식]" */
        APPENDIX("^\\[\\s*별\\s*[표지][^\\]]{0,20}\\]", true),
        ROMAN("^(?:[ⅠⅡⅢⅣⅤⅥⅦⅧⅨⅩ]|[IVX]{1,4}\\.)\\s*", false),
        BRACKET("^【[^】]{1,30}】", false),
        /** "3.1.3. 외국인근로자 전용보험", "3.1. 근로", "2.1.4 임금" (점이 둘 이상이거나 끝에 점). 단계는 숫자 개수로 가른다 */
        DECIMAL("^\\d{1,2}(?:\\.\\d{1,2}){1,3}\\.\\s+|^\\d{1,2}(?:\\.\\d{1,2}){2,3}\\s+", false),
        SQUARE("^■\\s*", true),
        SYMBOL("^(?:[□▶▷●◆◇◎▣❶-❿➊-➓]|" + PRIVATE_USE + ")\\s*", false),
        NUM_DOT("^\\d{1,2}\\.\\s+", false),
        HANGUL_DOT("^[가-하]\\.\\s*", false),
        NUM_PAREN("^\\d{1,2}\\)\\s*", false),
        PAREN_NUM("^\\(\\d{1,2}\\)\\s*", false),
        CIRCLED("^[①-⑳]\\s*", false),
        /** 아래 넷은 글자로 정하지 않고 {@link BundledDocuments} 가 붙인다: 표제, 로마 숫자 절, 상자 번호, 붙임 */
        BUNDLE_TITLE(null, false),
        BUNDLE_SECTION(null, false),
        BUNDLE_BOX(null, false),
        BUNDLE_ATTACHMENT(null, false),
        NONE(null, false);

        final Pattern pattern;
        final boolean strong;

        Marker(String regex, boolean strong) {
            this.pattern = regex == null ? null : Pattern.compile(regex);
            this.strong = strong;
        }

        /** 앞뒤 줄과 상관없이 제목 */
        boolean bundled() {
            return this == BUNDLE_TITLE || this == BUNDLE_SECTION || this == BUNDLE_BOX || this == BUNDLE_ATTACHMENT;
        }

        /** 단계까지 정해진 제목. 상자 번호는 지침마다 "1." 의 위·아래가 달라서 처음 나온 순서로 정한다 */
        boolean fixedLevel() {
            return bundled() && this != BUNDLE_BOX;
        }

        /** 묶음 문서 안의 정해진 제목이 바로 아래에 들어갈 수 있는 제목 */
        boolean canContain(Marker child) {
            return switch (child) {
                case BUNDLE_ATTACHMENT -> this == BUNDLE_TITLE;
                case BUNDLE_SECTION -> this == BUNDLE_TITLE || this == CHAPTER;
                default -> false;
            };
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
     * @param synthetic  문서에 없는 제목을 넣은 줄 (05 체류관리 표 앞의 "체류관리")
     */
    private record Line(String text, int page, boolean blankBefore, boolean pageStart, Marker marker,
                        boolean table, boolean header, TableBlock tableBlock, float fontSize, boolean bold,
                        boolean emphasized, boolean fullWidth, boolean synthetic) {
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

    /**
     * 제목 경로 (위 단계부터, 문서 이름 제외).
     *
     * @param bundled 맨 위가 묶음 문서 표제
     */
    private record HeadingPath(List<String> texts, boolean bundled) {
        static final HeadingPath EMPTY = new HeadingPath(List.of(), false);

        static HeadingPath of(Deque<Line> path) {
            return new HeadingPath(path.stream().map(Line::text).toList(),
                    !path.isEmpty() && path.getFirst().marker() == Marker.BUNDLE_TITLE);
        }

        int size() {
            return texts.size();
        }

        /** 맨 아래 단계를 뺀 경로. 비어 있으면 null */
        HeadingPath parent() {
            return texts.isEmpty() ? null : new HeadingPath(texts.subList(0, texts.size() - 1), bundled);
        }
    }

    /**
     * 절: 제목 경로(위 단계부터, 문서 이름 제외)와 조각 첫 줄 제목, 순서대로의 문단({@link Paragraph})·표({@link TableBlock})
     */
    private record Section(HeadingPath path, String title, List<Object> items) {
    }

    /**
     * 내보내기 전 조각.
     *
     * @param path     제목 경로. 형제 절을 묶은 조각({@code group})은 공통 부모 경로
     * @param overlap  첫 단위가 앞 조각 끝을 겹친 것 (합칠 때 뺀다)
     * @param group    형제 절을 묶은 조각 (하위 제목이 본문 안에 줄로 있다)
     * @param lastPath 마지막으로 넣은 절의 경로 (묶지 않은 조각은 path)
     * @param continuation 절의 첫 조각이 아니다 (앞 조각에서 이어지는 꼬리)
     */
    private record Draft(HeadingPath path, String title, List<Unit> units, boolean structuredTable, boolean overlap,
                         boolean group, HeadingPath lastPath, boolean continuation) {

        Draft(HeadingPath path, String title, List<Unit> units, boolean structuredTable, boolean overlap) {
            this(path, title, units, structuredTable, overlap, false, path, false);
        }

        Draft asContinuation() {
            return new Draft(path, title, units, structuredTable, overlap, group, lastPath, true);
        }

        Draft withUnits(List<Unit> newUnits, boolean newOverlap) {
            return new Draft(path, title, newUnits, structuredTable, newOverlap, group, lastPath, continuation);
        }

        int pageStart() {
            return units.stream().mapToInt(Unit::pageStart).min().orElse(0);
        }

        int pageEnd() {
            return units.stream().mapToInt(Unit::pageEnd).max().orElse(0);
        }

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
        // 글자 크기를 아는 출처(PLAIN, TAGGED)는 강조·단계 판단에 쓴다
        List<Line> lines = toLines(prepared, source != null);
        if (source == Source.PLAIN) {
            lines = PartTimeRow.rewrite(StayTable.rewrite(lines));
            lines = BundledDocuments.mark(lines, BodyStyle.of(prepared).medianSize());
        }
        boolean[] headings = findHeadings(lines, rules);
        // 표지 제목: 앞 3쪽의 가장 큰 첫 제목, 그리고 절반 넘게 경로 맨 앞에 붙는 제목 (부제가 이어지면 그것도)
        java.util.Set<String> covers = new java.util.HashSet<>();
        String first = coverHeading(lines, headings);
        if (first != null) {
            covers.add(first);
        }
        List<Section> sections = toSections(lines, headings, fallbackTitle, covers);
        for (int round = 0; round < COVER_ROUNDS; round++) {
            String majority = majorityTopTitle(sections);
            if (majority == null || !covers.add(majority)) {
                break;
            }
            sections = toSections(lines, headings, fallbackTitle, covers);
        }

        List<Draft> drafts = new ArrayList<>();
        for (Section section : sections) {
            pack(section, drafts);
        }
        List<Chunk> chunks = new ArrayList<>();
        for (Draft draft : mergeSiblings(mergeShort(drafts), fallbackTitle)) {
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

    /** 장-절 번호("1-4")는 조각이 아니다: 제목 줄과 합친다 ({@link #mergeSectionNumbers}) */
    private static boolean isFragment(String text) {
        return !SECTION_NUMBER_ONLY.matcher(text).matches()
                && (text.length() <= FRAGMENT_MAX || !LETTER_OR_DIGIT.matcher(text).find());
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
                            textLine.fontSize(), textLine.bold(), body.emphasized(textLine),
                            textLine.fullWidth() && !body.boxTitle(textLine), false));
                    previousPage = textLine.page();
                }
                case TableBlock table -> {
                    if (table.rows().isEmpty()) {
                        continue;
                    }
                    int page = table.firstPage();
                    lines.add(new Line("", page, true, page != previousPage, Marker.NONE, false, false, table,
                            0, false, false, false, false));
                    previousPage = table.rows().getLast().page();
                }
            }
        }
        return mergeAppendixTitles(mergeSectionNumbers(lines));
    }

    private static final Pattern AMENDMENT_NOTE = Pattern.compile("\\s*<[^>]*>");
    private static final Pattern APPENDIX_ONLY = Pattern.compile("^\\[[^\\]]*\\]$");

    /**
     * 별표 머리 줄("[별표 2] <개정 2019.7.11., …>")의 개정 표기를 떼고, 바로 아래 짧은 제목 줄을 잇는다
     * ("[별표 2] 지역보험료 산정 기준(제6조제1항 관련)").
     */
    private static List<Line> mergeAppendixTitles(List<Line> lines) {
        List<Line> merged = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            if (line.marker() != Marker.APPENDIX) {
                merged.add(line);
                continue;
            }
            String text = AMENDMENT_NOTE.matcher(line.text()).replaceAll("").strip();
            Line next = i + 1 < lines.size() ? lines.get(i + 1) : null;
            if (APPENDIX_ONLY.matcher(text).matches() && next != null && next.page() == line.page()
                    && next.tableBlock() == null && next.marker() == Marker.NONE
                    && isPlausibleTitle(text + " " + next.text(), Marker.APPENDIX)
                    && !mostlyDigits(next.text()) && !NOT_A_TITLE.matcher(next.text()).find()) {
                text = text + " " + next.text();
                i++;
            } else if (!APPENDIX_ONLY.matcher(text).matches()) {
                // 대괄호 뒤에 본문이 이어지는 줄("[별표1의2] 의 24. 거주(F-2) 타목의 …")은 별표 제목이 아니다
                merged.add(new Line(line.text(), line.page(), line.blankBefore(), line.pageStart(), Marker.NONE,
                        line.table(), line.header(), null, line.fontSize(), line.bold(), line.emphasized(),
                        line.fullWidth(), false));
                continue;
            }
            merged.add(withText(line, text));
        }
        return mergeChapterNumbers(merged);
    }

    /** 번호만 있는 장 머리 줄 ("PART 1.", "제2장") */
    private static final Pattern CHAPTER_NUMBER_ONLY = Pattern.compile("^(?:제\\s*\\d+\\s*[장편부]|PART\\s*\\d+)\\.?$");

    /** 번호만 있는 장 머리 줄("PART 1.")을 같은 쪽 바로 아래 짧은 장 이름("생활 정보")과 잇는다 ("PART 1. 생활 정보") */
    private static List<Line> mergeChapterNumbers(List<Line> lines) {
        List<Line> merged = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            Line next = i + 1 < lines.size() ? lines.get(i + 1) : null;
            if (CHAPTER_NUMBER_ONLY.matcher(line.text()).matches() && isPlainTitle(next, line.page())) {
                merged.add(withText(line, line.text() + " " + next.text()));
                i++;
            } else {
                merged.add(line);
            }
        }
        return merged;
    }

    /**
     * 본문 글자 모양: 글자 수 가중 중앙값 크기, 굵은 글자 비율.
     * 글자가 {@value #PAGE_STYLE_MIN_CHARS}자 이상인 쪽은 쪽의 중앙값도 둔다: 별표 쪽처럼 쪽 전체의 글자 배율이 크면
     * 그 쪽의 본문 줄이 모두 "강조" 로 보여 표 칸이 제목이 되므로, 문서와 쪽 중앙값 중 큰 쪽을 기준으로 한다.
     */
    private record BodyStyle(float medianSize, boolean mostlyBold, Map<Integer, Float> pageMedians) {
        static final BodyStyle UNKNOWN = new BodyStyle(0, true, Map.of());

        static BodyStyle of(List<ContentBlock> blocks) {
            List<float[]> sizes = new ArrayList<>();
            Map<Integer, List<float[]>> pageSizes = new HashMap<>();
            long total = 0;
            long bold = 0;
            for (ContentBlock block : blocks) {
                if (block instanceof TextLine line && line.fontSize() > 0) {
                    int length = line.text().length();
                    float[] size = {line.fontSize(), length};
                    sizes.add(size);
                    pageSizes.computeIfAbsent(line.page(), key -> new ArrayList<>()).add(size);
                    total += length;
                    if (line.bold()) {
                        bold += length;
                    }
                }
            }
            if (total == 0) {
                return UNKNOWN;
            }
            Map<Integer, Float> pageMedians = new HashMap<>();
            pageSizes.forEach((page, list) -> {
                if (list.stream().mapToDouble(size -> size[1]).sum() >= PAGE_STYLE_MIN_CHARS) {
                    pageMedians.put(page, median(list));
                }
            });
            return new BodyStyle(median(sizes), bold * 2 >= total, pageMedians);
        }

        /** {크기, 글자 수} 의 글자 수 가중 중앙값 */
        private static float median(List<float[]> sizes) {
            List<float[]> sorted = new ArrayList<>(sizes);
            sorted.sort((a, b) -> Float.compare(a[0], b[0]));
            double total = sorted.stream().mapToDouble(size -> size[1]).sum();
            double seen = 0;
            for (float[] size : sorted) {
                seen += size[1];
                if (seen * 2 >= total) {
                    return size[0];
                }
            }
            return sorted.getLast()[0];
        }

        float bodySize(int page) {
            return Math.max(medianSize, pageMedians.getOrDefault(page, 0f));
        }

        /**
         * 문서 본문보다 {@value #EMPHASIS_RATIO}배 이상 크고, 그 쪽 본문보다도 {@value #PAGE_EMPHASIS_RATIO}배 이상 크면 강조.
         * 별표 쪽처럼 쪽 전체 글자가 큰 쪽의 표 칸(쪽 본문과 같은 크기)은 강조가 아니고,
         * 큰 글자 본문 쪽의 제목(02 p4: 본문 125, 제목 133)은 강조다. 번호·기호가 있는 줄은 문서 기준만 본다
         */
        boolean emphasized(TextLine line) {
            if (medianSize <= 0 || line.fontSize() <= 0) {
                return false;
            }
            if (line.bold() && !mostlyBold) {
                return true;
            }
            if (line.fontSize() < medianSize * EMPHASIS_RATIO) {
                return false;
            }
            Float page = pageMedians.get(line.page());
            return Marker.of(line.text().strip()) != Marker.NONE || page == null
                    || line.fontSize() >= page * PAGE_EMPHASIS_RATIO;
        }

        /** 쪽 오른쪽 끝까지 찼어도 제목으로 볼 만큼 큰 줄 (상자 제목) */
        boolean boxTitle(TextLine line) {
            return medianSize > 0 && line.fontSize() >= bodySize(line.page()) * BOX_TITLE_RATIO;
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
                        next.fullWidth(), false),
                        line.text() + " " + next.text()));
                i++;
            } else {
                merged.add(line);
            }
        }
        return merged;
    }

    /**
     * 05 체류 매뉴얼의 체류관리 표 (태그 없는 PDF). 왼쪽 칸 이름이 행 가운데에 놓여서 화면 순서로 읽으면 값 줄 사이에 끼고
     * ("체류기간 / 해당사항 없음 / 연장허가", "… 재입국허가 / 면제 / 재입국허가 출국한 날부터 …"),
     * 둘로 나뉜 이름은 값의 위아래에, 한 줄 이름은 값의 가운데 줄 앞에 붙는다.
     * <ul>
     *     <li>칸 이름 사전({@link #LABELS})과 맞는 줄(이름만 있거나 이름으로 시작)을 찾는다. 서로 {@value #REGION_GAP}줄 안에
     *         이어진 것이 {@value #MIN_EVENTS}개 이상이면 표로 본다</li>
     *     <li>이름은 행 가운데에 있으므로, 이름 앞 줄 수만큼 이름 뒤 줄을 같은 행으로 본다. 남는 줄은 다음 행 앞 줄이다.
     *         마지막 행은 쪽이 바뀌거나 강조·번호 줄이 나올 때까지 ({@value #LAST_ROW_MAX}줄까지)</li>
     *     <li>행마다 "칸 이름: 값" 한 줄로 바꾸고, 표 앞에 자격 제목 바로 아래 단계의 "{@value #TITLE}" 제목을 넣는다</li>
     * </ul>
     */
    static final class StayTable {

        static final String TITLE = "체류관리";
        static final int REGION_GAP = 3;
        static final int MIN_EVENTS = 2;
        static final int SPLIT_MAX_GAP = 12;
        static final int LAST_ROW_MAX = 6;
        /** 자격 제목 ("영 주(F-5)", "비전문취업(E-9)") */
        static final Pattern QUALIFICATION = Pattern.compile("\\(\\s*[A-H]\\s*-\\s*\\d{1,2}\\s*\\)\\s*$");

        /** 칸 이름과, 두 줄로 나뉠 때의 윗줄·아랫줄 (공백·가운뎃점 뺀 모양). 한 줄 이름은 second 가 null */
        record Label(String name, String first, String second) {
        }

        static final List<Label> LABELS = List.of(
                new Label("체류자격외 활동", "체류자격외", "활동"),
                new Label("근무처의 변경·추가", "근무처의", "변경추가"),
                new Label("체류자격 부여", "체류자격", "부여"),
                new Label("체류자격 변경허가", "체류자격", "변경허가"),
                new Label("체류기간 연장허가", "체류기간", "연장허가"),
                new Label("재입국허가", "재입국허가", null),
                new Label("외국인등록", "외국인등록", null));

        /** 칸 이름이 있는 줄 범위 [start, end] 와 그 줄들에서 이름을 뗀 글자 */
        private record Event(Label label, int start, int end, List<String> spanTexts) {
        }

        static List<Line> rewrite(List<Line> lines) {
            List<Event> events = findEvents(lines);
            if (events.size() < MIN_EVENTS) {
                return lines;
            }
            List<Line> out = new ArrayList<>(lines.size());
            int next = 0;
            int e = 0;
            while (e < events.size()) {
                // 이어진 이름들을 한 표로
                int regionEnd = e;
                while (regionEnd + 1 < events.size()
                        && events.get(regionEnd + 1).start() - events.get(regionEnd).end() - 1 <= REGION_GAP) {
                    regionEnd++;
                }
                List<Event> region = events.subList(e, regionEnd + 1);
                e = regionEnd + 1;
                if (region.size() < MIN_EVENTS) {
                    continue;
                }
                int first = region.getFirst().start();
                for (; next < first; next++) {
                    out.add(lines.get(next));
                }
                Line firstLine = lines.get(first);
                out.add(new Line(TITLE, firstLine.page(), true, firstLine.pageStart(), Marker.NONE, false, false, null,
                        firstLine.fontSize(), false, false, false, true));
                int before = 0;
                int cursor = first;
                for (int k = 0; k < region.size(); k++) {
                    Event event = region.get(k);
                    int after;
                    if (k + 1 < region.size()) {
                        int gap = region.get(k + 1).start() - event.end() - 1;
                        after = Math.min(gap, before);
                    } else {
                        after = lastRowLength(lines, event.end());
                    }
                    StringBuilder value = new StringBuilder();
                    for (int i = cursor; i < event.start(); i++) {
                        append(value, lines.get(i).text());
                    }
                    for (String text : event.spanTexts()) {
                        append(value, text);
                    }
                    for (int i = event.end() + 1; i <= event.end() + after; i++) {
                        append(value, lines.get(i).text());
                    }
                    Line page = lines.get(cursor);
                    String text = value.isEmpty() ? event.label().name() : event.label().name() + ": " + value;
                    out.add(new Line(text, page.page(), true, page.pageStart(), Marker.NONE, false, false, null,
                            page.fontSize(), false, false, false, false));
                    cursor = event.end() + after + 1;
                    before = k + 1 < region.size() ? region.get(k + 1).start() - cursor : 0;
                }
                // 표 끝: 뒤 내용은 "체류관리" 아래로 들지 않는다
                Line end = lines.get(cursor - 1);
                out.add(new Line("", end.page(), true, false, Marker.NONE, false, false, null, 0, false, false, false,
                        true));
                next = cursor;
            }
            for (; next < lines.size(); next++) {
                out.add(lines.get(next));
            }
            return out;
        }

        private static void append(StringBuilder value, String text) {
            if (!text.isBlank()) {
                if (!value.isEmpty()) {
                    value.append(' ');
                }
                value.append(text.strip());
            }
        }

        /** 마지막 행의 이름 뒤 줄 수: 같은 쪽에서 강조·번호 줄·표 전까지 */
        private static int lastRowLength(List<Line> lines, int end) {
            int count = 0;
            for (int i = end + 1; i < lines.size() && count < LAST_ROW_MAX; i++) {
                Line line = lines.get(i);
                if (line.page() != lines.get(end).page() || line.emphasized() || line.tableBlock() != null
                        || (line.marker() != Marker.NONE && line.marker() != Marker.CIRCLED) || isAnyLabel(line.text())) {
                    break;
                }
                count++;
            }
            return count;
        }

        private static List<Event> findEvents(List<Line> lines) {
            List<Event> events = new ArrayList<>();
            for (int i = 0; i < lines.size(); i++) {
                Event event = eventAt(lines, i);
                // 같은 이름이 바로 다시 나오면 값 속 낱말 ("… 경우 / 재입국허가 면제")
                Event previous = events.isEmpty() ? null : events.getLast();
                if (event != null && previous != null && previous.label().equals(event.label())
                        && event.start() - previous.end() - 1 <= SPLIT_MAX_GAP) {
                    continue;
                }
                if (event != null) {
                    events.add(event);
                    i = event.end();
                }
            }
            return events;
        }

        private static Event eventAt(List<Line> lines, int i) {
            String text = lines.get(i).text();
            if (lines.get(i).tableBlock() != null) {
                return null;
            }
            // 한 줄 이름 (이름만 있거나 이름 + 값)
            for (Label label : LABELS) {
                String rest = rest(text, normalize(label.name()));
                if (rest != null) {
                    return new Event(label, i, i, List.of(rest));
                }
            }
            // 두 줄로 나뉜 이름: 윗줄과 가장 가까운 아랫줄
            for (Label label : LABELS) {
                if (label.second() == null) {
                    continue;
                }
                String firstRest = rest(text, label.first());
                if (firstRest == null) {
                    continue;
                }
                for (int j = i + 1; j < lines.size() && j <= i + SPLIT_MAX_GAP; j++) {
                    String secondRest = rest(lines.get(j).text(), label.second());
                    // 본문 문장("체류자격 구분에 따른 …")과 헷갈리지 않게 두 줄 중 하나는 이름만 있는 줄
                    if (secondRest != null && (firstRest.isEmpty() || secondRest.isEmpty())) {
                        List<String> span = new ArrayList<>();
                        span.add(firstRest);
                        for (int k = i + 1; k < j; k++) {
                            span.add(lines.get(k).text());
                        }
                        span.add(secondRest);
                        return new Event(label, i, j, span);
                    }
                    if (isAnyLabel(lines.get(j).text())) {
                        break;
                    }
                }
            }
            return null;
        }

        private static boolean isAnyLabel(String text) {
            for (Label label : LABELS) {
                if (rest(text, normalize(label.name())) != null || rest(text, label.first()) != null) {
                    return true;
                }
            }
            return false;
        }

        /**
         * 줄이 이름(공백·가운뎃점 무시)으로 시작하면 이름 뒤 글자 (이름만 있으면 ""), 아니면 null.
         * 이름 바로 뒤가 공백이어야 한다 ("재입국허가를 받아야" 는 아니다)
         */
        static String rest(String text, String normalizedName) {
            int matched = 0;
            int i = 0;
            while (i < text.length() && matched < normalizedName.length()) {
                char c = text.charAt(i);
                if (isIgnorable(c)) {
                    i++;
                    continue;
                }
                if (c != normalizedName.charAt(matched)) {
                    return null;
                }
                matched++;
                i++;
            }
            if (matched < normalizedName.length()) {
                return null;
            }
            if (i == text.length()) {
                return "";
            }
            return Character.isWhitespace(text.charAt(i)) ? text.substring(i).strip() : null;
        }

        private static boolean isIgnorable(char c) {
            return Character.isWhitespace(c) || c == '·' || c == '․' || c == 'ㆍ' || c == '‧' || c == ' ';
        }

        static String normalize(String text) {
            StringBuilder out = new StringBuilder();
            for (char c : text.toCharArray()) {
                if (!isIgnorable(c)) {
                    out.append(c);
                }
            }
            return out.toString();
        }
    }

    /**
     * 05 체류 매뉴얼 뒤에 통째로 묶인 지침 6개 (목차 36~41: 동포, 지역특화형, 청소년, 톱티어, 광역형, K-STAR).
     * 특례 조각에 어느 지침인지 남지 않으면 일반 규정으로 읽히므로 표제를 다음 표제 전까지 경로 맨 위에 고정한다.
     * <ul>
     *     <li>표제: 마지막 체류자격 장 뒤, 쪽 맨 위(또는 "- 부제 -" 바로 아래)의 큰 글자이고 매뉴얼·지침·알림·시범사업으로 끝남.
     *         그 자격 코드가 든 상자 제목("코로나19 관련 영주(F-5)… 알림")은 아니다</li>
     *     <li>표제 안에서는 글자 크기가 본문과 같아도 로마 숫자는 절, 상자 번호(󰊱)도 제목, "붙임 3"·"별첨 2"·"참고 1"은 표제 바로 아래.
     *         위 단계를 제목에 고정하므로, 이 줄들을 놓치면 앞 절 이름이 다음 절 내용에 붙는다</li>
     * </ul>
     */
    static final class BundledDocuments {

        /** 본문보다 이 배수 이상 큰 표제·자격 장 제목 */
        static final float TITLE_RATIO = 1.5f;
        static final int SECTION_MAX = 60;
        private static final Pattern TITLE = Pattern.compile("(?:매뉴얼|메뉴얼|지침|알림|시범사업)$");
        private static final Pattern BOX = Pattern.compile("^[\\x{F02B1}-\\x{F02BA}]");
        private static final Pattern ATTACHMENT = Pattern.compile(
                "^(?:【\\s*)?(?:붙임|별첨|참고)\\s*[0-9０-９]{1,2}(?:-\\d)?(?:\\s*】|\\s|$)");

        static List<Line> mark(List<Line> lines, float median) {
            if (median <= 0) {
                return lines;
            }
            float titleSize = median * TITLE_RATIO;
            int lastChapter = -1;
            for (int i = 0; i < lines.size(); i++) {
                Line line = lines.get(i);
                if (line.pageStart() && line.marker() == Marker.NONE && line.emphasized()
                        && line.fontSize() >= titleSize && StayTable.QUALIFICATION.matcher(line.text()).find()) {
                    lastChapter = i;
                }
            }
            if (lastChapter < 0) {
                return lines;
            }
            Matcher code = StayTable.QUALIFICATION.matcher(lines.get(lastChapter).text());
            String chapterCode = code.find() ? StayTable.normalize(code.group()) : "";

            List<Line> out = new ArrayList<>(lines.subList(0, lastChapter + 1));
            boolean inside = false;
            for (int i = lastChapter + 1; i < lines.size(); i++) {
                Line line = lines.get(i);
                Line previous = lines.get(i - 1);
                boolean belowSubtitle = previous.pageStart() && previous.page() == line.page()
                        && BULLET.matcher(previous.text()).find();
                if ((line.pageStart() || belowSubtitle) && line.page() > COVER_MAX_PAGE
                        && line.marker() == Marker.NONE && line.emphasized() && line.fontSize() >= titleSize
                        && line.text().length() <= HEADING_MAX && TITLE.matcher(line.text()).find()
                        && !StayTable.normalize(line.text()).contains(chapterCode)) {
                    // 표제 앞 부제는 새 문서의 내용
                    out.add(belowSubtitle ? out.size() - 1 : out.size(), withMarker(line, Marker.BUNDLE_TITLE));
                    inside = true;
                    continue;
                }
                Line next = i + 1 < lines.size() ? lines.get(i + 1) : null;
                out.add(inside ? withMarker(line, markerInside(line, previous, next)) : line);
            }
            return out;
        }

        private static Marker markerInside(Line line, Line previous, Line next) {
            String text = line.text();
            if (text.length() > SECTION_MAX || TOC_LINE.matcher(text).find()) {
                return line.marker();
            }
            if (line.marker() == Marker.ROMAN && line.emphasized()) {
                return Marker.BUNDLE_SECTION;
            }
            if ((line.pageStart() || line.emphasized()) && ATTACHMENT.matcher(text).find()) {
                return Marker.BUNDLE_ATTACHMENT;
            }
            // 상자 번호가 이어지면 목록 (표지의 "내용")
            if (line.emphasized() && !line.fullWidth() && text.length() <= HEADING_MAX && BOX.matcher(text).find()
                    && !BOX.matcher(previous.text()).find() && (next == null || !BOX.matcher(next.text()).find())) {
                return Marker.BUNDLE_BOX;
            }
            return line.marker();
        }
    }

    /**
     * 05 체류 매뉴얼 유학(D-2)의 "시간제취업 / 활동 허가" 칸 (p37~40).
     * 왼쪽 칸 이름이 쪽마다 값 앞 두 줄에 붙어 나와 제목이 되지 못하고, 앞의 배우자(F-3) 제목이 일반 규정에 붙는다.
     * 이름을 떼어 자격 제목 바로 아래 제목으로 넣고, 다음 칸 이름이나 자격 장에서 끝낸다. 칸 안의 "가." 는 제목이 될 수 있다.
     */
    static final class PartTimeRow {

        static final String TITLE = "시간제취업 활동 허가";
        private static final String FIRST = "시간제취업";
        private static final String SECOND = "활동허가";

        static List<Line> rewrite(List<Line> lines) {
            List<Line> out = new ArrayList<>(lines.size());
            boolean underQualification = false;
            boolean open = false;
            for (int i = 0; i < lines.size(); i++) {
                Line line = lines.get(i);
                Line next = i + 1 < lines.size() ? lines.get(i + 1) : null;
                boolean qualification = line.emphasized() && StayTable.QUALIFICATION.matcher(line.text()).find();
                underQualification |= qualification;

                String first = underQualification ? StayTable.rest(line.text(), FIRST) : null;
                String second = first == null || next == null || next.page() != line.page()
                        ? null : StayTable.rest(next.text(), SECOND);
                if (second != null) {
                    if (!open) {
                        out.add(new Line(TITLE, line.page(), true, line.pageStart(), Marker.NONE, false, false, null,
                                line.fontSize(), false, false, false, true));
                        open = true;
                    }
                    addValue(out, line, first, true);
                    addValue(out, next, second, false);
                    i++;
                    continue;
                }
                if (open && (qualification || line.synthetic() || startsNextRow(line, next))) {
                    out.add(new Line("", line.page(), true, false, Marker.NONE, false, false, null, 0, false, false,
                            false, true));
                    open = false;
                }
                if (open && line.marker() == Marker.HANGUL_DOT) {
                    // "나. 대 상": 앞 줄이 문장으로 끝나지 않고 칸 끝까지 찼어도 새 블록
                    if (out.getLast().fullWidth()) {
                        out.set(out.size() - 1, startingBlock(out.getLast(), out.getLast().blankBefore()));
                    }
                    line = startingBlock(line, true);
                }
                out.add(line);
            }
            return out;
        }

        private static void addValue(List<Line> out, Line line, String value, boolean first) {
            if (!value.isEmpty()) {
                out.add(new Line(value, line.page(), first || line.blankBefore(), first && line.pageStart(),
                        Marker.of(value), false, false, null, line.fontSize(), line.bold(), line.emphasized(),
                        line.fullWidth(), false));
            }
        }

        private static Line startingBlock(Line line, boolean blankBefore) {
            return new Line(line.text(), line.page(), blankBefore, line.pageStart(), line.marker(), line.table(),
                    line.header(), line.tableBlock(), line.fontSize(), line.bold(), line.emphasized(), false,
                    line.synthetic());
        }

        /** 두 줄로 나뉜 다음 칸 이름 ("근무처의 …" / "변경․추가 …") */
        private static boolean startsNextRow(Line line, Line next) {
            if (next == null) {
                return false;
            }
            for (StayTable.Label label : StayTable.LABELS) {
                if (label.second() != null && StayTable.rest(line.text(), label.first()) != null
                        && StayTable.rest(next.text(), label.second()) != null) {
                    return true;
                }
            }
            return false;
        }
    }

    private static Line withMarker(Line line, Marker marker) {
        if (marker == line.marker()) {
            return line;
        }
        // 정해진 제목은 표 줄·이어지는 줄로 보지 않는다
        return new Line(line.text(), line.page(), true, line.pageStart(), marker, false, false, null,
                line.fontSize(), line.bold(), line.emphasized(), false, false);
    }

    private static boolean isPlainTitle(Line line, int page) {
        return line != null && line.page() == page && line.tableBlock() == null
                && line.marker() == Marker.NONE && isHeadingCandidate(line, Rules.LENIENT);
    }

    private static Line withText(Line line, String text) {
        Marker marker = Marker.of(text);
        return new Line(text, line.page(), line.blankBefore(), line.pageStart(), marker, false, false, null,
                line.fontSize(), line.bold(), line.emphasized(), line.fullWidth(), line.synthetic());
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
            if (lines.get(i).marker().bundled()) {
                candidate[i] = true;
                canStart[i] = true;
                continue;
            }
            canStart[i] = startsBlock(lines, i, rules)
                    || (i > 0 && candidate[i - 1] && (canStart[i - 1] || lines.get(i - 1).marker().strong)
                    && followsHeading(lines.get(i - 1), lines.get(i)));
        }
        boolean[] heading = new boolean[n];
        // 뒤에서부터: "2. 신청 / 가. 기한 / 본문" 처럼 제목이 이어지면 다음 줄이 제목이어도 된다
        for (int i = n - 1; i >= 0; i--) {
            Line line = lines.get(i);
            if (line.synthetic() || line.marker().bundled()) {
                heading[i] = true;
                continue;
            }
            // 표제 바로 아래 번호 없는 줄은 부제 ("사증발급 및 체류관리지침 제정(안)")
            if (i > 0 && lines.get(i - 1).marker() == Marker.BUNDLE_TITLE && line.marker() == Marker.NONE) {
                continue;
            }
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

    /** 제목 모양: 번호·기호 종류 ("3.1.3." 은 숫자 개수까지), 번호 없으면 글자 크기·굵기 */
    private static String shapeKey(Line line) {
        if (line.synthetic()) {
            return "SYNTHETIC";
        }
        if (line.marker() == Marker.DECIMAL) {
            return "DECIMAL" + line.text().split("\\s", 2)[0].replaceAll("\\.$", "").split("\\.").length;
        }
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
        return text.length() <= (marker == Marker.APPENDIX ? APPENDIX_HEADING_MAX : HEADING_MAX)
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
        if (marker == Marker.NONE || marker == Marker.CHAPTER || marker == Marker.SECTION || marker == Marker.ARTICLE
                || marker == Marker.DECIMAL || marker == Marker.APPENDIX) {
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
        // PLAIN: 앞 줄보다 큰 "Ⅲ"·"5." 는 앞 내용이 문장으로 끝나지 않았어도 새 제목 ("□ 쿼터 : 250명 / Ⅲ …")
        if (rules.strict() && (line.marker() == Marker.ROMAN || line.marker() == Marker.NUM_DOT)
                && line.emphasized() && isSmaller(prev, line)) {
            return true;
        }
        // PLAIN: 앞 줄이 오른쪽 끝까지 차고 문장이 끝나지 않았으면 이어지는 줄 (쪽이 바뀌어도)
        // 단, 새 쪽 첫 줄이 강조된 줄이면 장 제목 ("… 장 귀하" 로 끝난 서식 쪽 다음의 "기술연수(D-3)")
        if (rules.strict() && prev.fullWidth() && !SENTENCE_END.matcher(prev.text()).find()
                && !PREDICATE_END.matcher(prev.text()).find() && !(line.pageStart() && line.emphasized())) {
            return false;
        }
        if (line.pageStart()) {
            return true;
        }
        if (prev.tableBlock() != null || prev.synthetic()) {
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
    /** @param covers 표지 제목 글자 (같은 글자의 제목은 경로에 넣지 않는다) */
    private static List<Section> toSections(List<Line> lines, boolean[] headings, String fallbackTitle,
                                            java.util.Set<String> covers) {
        List<Section> sections = new ArrayList<>();
        Deque<Line> path = new ArrayDeque<>();
        Map<String, Integer> levels = new HashMap<>();
        Map<String, Integer> levelsBeforeRow = null;
        Section section = new Section(HeadingPath.EMPTY, fallbackTitle, new ArrayList<>());
        sections.add(section);
        Paragraph paragraph = null;
        Line prev = null;

        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            if (headings[i] && line.synthetic() && line.text().isEmpty()) {
                // 넣은 제목의 끝: 그 제목을 경로에서 빼고 자격 제목 아래로 돌아간다
                if (path.stream().anyMatch(Line::synthetic)) {
                    while (!path.isEmpty() && !path.peekLast().synthetic()) {
                        path.removeLast();
                    }
                    path.removeLast();
                    if (levelsBeforeRow != null) {
                        levels.clear();
                        levels.putAll(levelsBeforeRow);
                    }
                    section = new Section(HeadingPath.of(path), path.isEmpty() ? fallbackTitle : title(path),
                            new ArrayList<>());
                    sections.add(section);
                    paragraph = null;
                }
                levelsBeforeRow = null;
                prev = line;
                continue;
            }
            if (headings[i] && line.synthetic()) {
                // 넣은 제목("체류관리")은 자격 제목 바로 아래 단계. 위에 자격 제목이 없으면 넣지 않는다 (수수료 표 등)
                if (path.stream().noneMatch(p -> StayTable.QUALIFICATION.matcher(p.text()).find())) {
                    continue;
                }
                while (!path.isEmpty() && !StayTable.QUALIFICATION.matcher(path.peekLast().text()).find()) {
                    path.removeLast();
                }
                int parentLevel = path.isEmpty() ? 0 : levels.get(shapeKey(path.peekLast()));
                if (line.text().equals(PartTimeRow.TITLE)) {
                    // 칸 안의 "가."·"(1)" 은 칸 제목 아래 단계로 새로 정하고, 칸이 끝나면 되돌린다
                    levelsBeforeRow = new HashMap<>(levels);
                    levels.keySet().retainAll(path.stream().map(DocumentChunker::shapeKey).toList());
                }
                levels.put(shapeKey(line), parentLevel + 1);
                path.addLast(line);
                section = new Section(HeadingPath.of(path), title(path), new ArrayList<>());
                sections.add(section);
                paragraph = null;
                prev = line;
                continue;
            }
            if (headings[i] && line.marker().fixedLevel()) {
                if (line.marker() == Marker.BUNDLE_TITLE) {
                    path.clear();
                } else {
                    while (!path.isEmpty() && !path.peekLast().marker().canContain(line.marker())) {
                        path.removeLast();
                    }
                }
                keepEmptyHeading(sections, path, i > 0 && headings[i - 1] ? prev : null);
                path.addLast(line);
                // 정해진 제목마다 그 아래 단계를 새로 센다: 앞 절에서 배운 단계가 이 절의 제목을 밀어내지 않게
                levels.clear();
                int depth = 0;
                for (Line heading : path) {
                    levels.put(shapeKey(heading), depth++);
                }
                section = new Section(HeadingPath.of(path), title(path), new ArrayList<>());
                sections.add(section);
                paragraph = null;
                prev = line;
                continue;
            }
            if (headings[i] && covers.contains(line.text())) {
                // 표지 제목(과 같은 글자의 제목)은 제목 경로에 넣지 않는다: 그 조각의 첫 줄로만 쓰고, 다음 제목부터 새로 단계를 센다
                path.clear();
                levels.clear();
                section = new Section(HeadingPath.EMPTY, line.text(), new ArrayList<>());
                sections.add(section);
                paragraph = null;
                prev = line;
                continue;
            }
            if (headings[i]) {
                // 글자가 더 큰 제목은 더 작은 제목 아래에 들지 않는다 ("방문동거(F-1)" 20pt 아래의 "영 주(F-5)" 24pt)
                // 표제는 다음 표제 전까지, 붙임은 다음 붙임 전까지 남는다 (서식 제목이 더 커도)
                while (!path.isEmpty() && path.peekLast().marker() != Marker.BUNDLE_TITLE
                        && path.peekLast().marker() != Marker.BUNDLE_ATTACHMENT && isSmaller(path.peekLast(), line)) {
                    path.removeLast();
                }
                // 장 제목과 같은 모양이면 새 장 (붙임 서식의 큰 글자 "학교 변경 동의서" 아래로 "기술연수(D-3)" 가 들지 않게).
                // 장 번호 제목("PART 1.", "제2장")은 늘 새 장 (표지의 큰 글자 아래로 들지 않게)
                if (Integer.valueOf(1).equals(levels.get(shapeKey(line))) || line.marker() == Marker.CHAPTER
                        || startsQualificationChapter(path, line)) {
                    while (!path.isEmpty() && path.peekLast().marker() != Marker.BUNDLE_TITLE) {
                        path.removeLast();
                    }
                }
                int parentLevel = path.isEmpty() ? 0 : levels.get(shapeKey(path.peekLast()));
                int level = levels.computeIfAbsent(shapeKey(line), key -> parentLevel + 1);
                boolean bundled = !path.isEmpty() && path.getFirst().marker() == Marker.BUNDLE_TITLE;
                // 묶음 문서: 제목 바로 밑 제목은 그 자식 ("1. 서울특별시 / □ 대상")
                if (bundled && level <= parentLevel && path.peekLast() == prev && !sameShape(prev, line)) {
                    level = parentLevel + 1;
                    levels.put(shapeKey(line), level);
                }
                while (!path.isEmpty() && path.peekLast().marker() != Marker.BUNDLE_TITLE
                        && levels.get(shapeKey(path.peekLast())) >= level) {
                    path.removeLast();
                }
                if (path.isEmpty()) {
                    // 최상위 제목(장)마다 그 아래 단계를 새로 정한다: 장마다 번호 체계가 달라도 앞 장의 단계가 남지 않게
                    levels.clear();
                    levels.put(shapeKey(line), 1);
                } else if (path.peekLast().marker() == Marker.BUNDLE_TITLE) {
                    levels.clear();
                    levels.put(shapeKey(path.peekLast()), 0);
                    levels.put(shapeKey(line), 1);
                }
                keepEmptyHeading(sections, path, i > 0 && headings[i - 1] ? prev : null);
                path.addLast(line);
                section = new Section(HeadingPath.of(path), title(path), new ArrayList<>());
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

    /**
     * 묶음 문서에서 내용 없이 바로 밀려난 제목(간지 "붙임 문서", 서식 끝 "추천기관 (직 인)")은 앞 절의 본문으로 남긴다.
     *
     * @param previous 바로 앞 줄이 제목일 때 그 줄, 아니면 null
     */
    private static void keepEmptyHeading(List<Section> sections, Deque<Line> path, Line previous) {
        if (previous == null || previous.synthetic() || sections.size() < 2 || path.isEmpty()
                || path.getFirst().marker() != Marker.BUNDLE_TITLE
                || path.stream().anyMatch(heading -> heading == previous)) {
            return;
        }
        Paragraph kept = new Paragraph(false);
        kept.add(previous);
        sections.get(sections.size() - 2).items().add(kept);
    }

    /**
     * 번호 없는 체류자격 제목("영 주(F-5)", "비전문취업(E-9)")은 늘 새 장이다 (05 는 자격마다 장이 시작된다).
     * 서식 제목 "K-point E74 고용기업 추천서" 나 앞 자격 아래로 들지 않게 한다. 번호 있는 "3) 영주(F-5-28)…" 는 아니다
     */
    private static boolean startsQualificationChapter(Deque<Line> path, Line line) {
        return !path.isEmpty() && path.getFirst().marker() != Marker.BUNDLE_TITLE
                && line.marker() == Marker.NONE && StayTable.QUALIFICATION.matcher(line.text()).find();
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
        return title(HeadingPath.of(path), null);
    }

    /**
     * 표지 제목의 두 번째 판단: 내용이 있는 절의 절반 넘게 경로 맨 앞에 같은 번호 없는 1단계 제목이 붙으면 표지
     * ("비자 내비게이터", "외국인체류 안 내 매 뉴 얼"). 번호가 있는 장("Ⅱ. 외국인 근로자의 연말정산")과 체류자격 제목("영 주(F-5)")은
     * 문서의 절반을 차지해도 장이다. 절이 {@value #MAJORITY_MIN_SECTIONS}개 미만인 짧은 문서는 보지 않는다. 없으면 null
     */
    static String majorityTopTitle(List<Section> sections) {
        Map<String, Integer> counts = new HashMap<>();
        int total = 0;
        for (Section section : sections) {
            if (section.items().isEmpty()) {
                continue;
            }
            total++;
            if (section.path().size() >= 2) {
                counts.merge(section.path().texts().getFirst(), 1, Integer::sum);
            }
        }
        if (total < MAJORITY_MIN_SECTIONS) {
            return null;
        }
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() * 2 > total && Marker.of(entry.getKey()) == Marker.NONE
                    && !StayTable.QUALIFICATION.matcher(entry.getKey()).find()) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** 표지 제목을 절반 규칙으로 찾는 최대 횟수 (표지 제목 아래 부제가 또 붙는 경우) */
    static final int COVER_ROUNDS = 3;

    /** 절 수가 이보다 적은 문서는 "절반 넘게 붙는 1단계 제목" 으로 표지를 정하지 않는다 */
    static final int MAJORITY_MIN_SECTIONS = 20;

    /** 표지로 보는 제목이 있는 쪽 (이 쪽까지) */
    static final int COVER_MAX_PAGE = 3;

    /**
     * 표지 제목: 문서의 첫 제목이 {@value #COVER_MAX_PAGE}쪽 안에 있고 다른 어떤 제목보다 글자가 크거나 같다
     * ("시흥 처음 어서와~" 34pt, "2025 외국인 근로자 연말정산 매뉴얼"). 없으면 null
     */
    private static String coverHeading(List<Line> lines, boolean[] headings) {
        Line first = null;
        float largestOther = 0;
        for (int i = 0; i < lines.size(); i++) {
            if (!headings[i] || lines.get(i).synthetic()) {
                continue;
            }
            if (first == null) {
                first = lines.get(i);
            } else {
                largestOther = Math.max(largestOther, lines.get(i).fontSize());
            }
        }
        if (first == null || first.page() > COVER_MAX_PAGE || first.fontSize() <= 0 || first.fontSize() < largestOther) {
            return null;
        }
        return first.text();
    }

    /**
     * 조각 첫 줄 제목: 가까운 {@value #TITLE_LEVELS}단계 ("상위 &gt; 하위"). {@value #TITLE_MAX}자를 넘으면 위 단계부터 뺀다.
     * 경로가 비면 fallback (문서 이름). 묶음 문서 안은 {@link #shownInBundle}
     */
    private static String title(HeadingPath headingPath, String fallback) {
        List<String> path = headingPath.texts();
        if (path.isEmpty()) {
            return fallback;
        }
        if (headingPath.bundled()) {
            return String.join(" > ", shownInBundle(path));
        }
        int from = Math.max(0, path.size() - TITLE_LEVELS);
        String title = String.join(" > ", path.subList(from, path.size()));
        while (title.length() > TITLE_MAX && from < path.size() - 1) {
            from++;
            title = String.join(" > ", path.subList(from, path.size()));
        }
        return title;
    }

    /**
     * 묶음 문서의 제목에 보일 단계: 위 {@value #BUNDLED_TOP_LEVELS}단계(표제·절·지역)와 마지막 단계.
     * {@value #BUNDLED_TITLE_MAX}자를 넘으면 아래 단계부터 뺀다. 뺀 단계는 본문 첫 줄에 남긴다 ({@link #packItems}).
     */
    private static List<String> shownInBundle(List<String> path) {
        List<String> shown = new ArrayList<>(path.subList(0, Math.min(BUNDLED_TOP_LEVELS, path.size())));
        if (path.size() > BUNDLED_TOP_LEVELS) {
            shown.add(path.getLast());
        }
        while (shown.size() > 1 && String.join(" > ", shown).length() > BUNDLED_TITLE_MAX) {
            shown.removeLast();
        }
        return shown;
    }

    // ---------------------------------------------------------------- 조각

    /** 문단은 목표 길이로 묶고(겹침 있음), 표는 앞뒤 문단과 떼어 따로 조각으로 만든다 */
    private static void pack(Section section, List<Draft> drafts) {
        int first = drafts.size();
        packItems(section, drafts);
        for (int i = first + 1; i < drafts.size(); i++) {
            drafts.set(i, drafts.get(i).asContinuation());
        }
    }

    private static void packItems(Section section, List<Draft> drafts) {
        HeadingPath path = section.path();
        String title = section.title();
        int titleLength = title == null ? 0 : title.length() + 1;
        int budget = Math.max(MIN_BUDGET, MAX - titleLength);
        int target = Math.max(MIN_BUDGET / 2, TARGET - titleLength);

        List<Unit> units = new ArrayList<>();
        if (path.bundled() && !section.items().isEmpty()) {
            List<String> omitted = new ArrayList<>(path.texts());
            shownInBundle(path.texts()).forEach(omitted::remove);
            if (!omitted.isEmpty()) {
                int page = section.items().getFirst() instanceof Paragraph first
                        ? first.linePages.getFirst() : ((TableBlock) section.items().getFirst()).firstPage();
                units.add(new Unit(String.join(" > ", omitted), page, page, false));
            }
        }
        for (Object item : section.items()) {
            if (item instanceof Paragraph paragraph) {
                units.addAll(toUnits(paragraph, budget));
            } else if (item instanceof TableBlock table) {
                packText(units, path, title, budget, target, drafts);
                units.clear();
                packTable(table, path, title, budget, drafts);
            }
        }
        packText(units, path, title, budget, target, drafts);
    }

    /**
     * 표 하나를 한 조각으로 (한 줄에 한 행, 행마다 열 이름). 길면 행 경계에서 나눈다.
     * 행마다 열 이름이 붙어 있어 앞 조각 없이도 읽히므로 겹치지 않는다.
     */
    private static void packTable(TableBlock table, HeadingPath path, String title, int budget, List<Draft> drafts) {
        List<Unit> current = new ArrayList<>();
        for (TableRow row : table.rows()) {
            Paragraph rowText = new Paragraph(true);
            rowText.add(row.render(), row.page());
            for (Unit unit : toUnits(rowText, budget)) {
                if (!current.isEmpty() && length(current) + 1 + unit.text().length() > budget) {
                    drafts.add(new Draft(path, title, current, true, false));
                    current = new ArrayList<>();
                }
                current.add(unit);
            }
        }
        if (!current.isEmpty()) {
            drafts.add(new Draft(path, title, current, true, false));
        }
    }

    /**
     * 문단을 목표 길이로 묶는다. 조각이 다음 줄로 이어지는 짧은 머리 문장("※ 외국인근로자에게 적용되는",
     * "… 다음과 같다:")으로 끝나면 그 문장은 다음 조각으로 넘긴다.
     */
    private static void packText(List<Unit> units, HeadingPath path, String title, int budget, int target,
                                 List<Draft> drafts) {
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
                int own = overlap ? 1 : 0;
                Unit leadIn = current.size() > own + 1 && isLeadIn(current.getLast())
                        && current.getLast().text().length() + 1 + unit.text().length() <= budget
                        ? current.removeLast() : null;
                carry = leadIn == null ? tail(current) : null;
                drafts.add(new Draft(path, title, current, false, overlap));
                current = new ArrayList<>();
                if (leadIn != null) {
                    current.add(leadIn);
                    overlap = false;
                } else {
                    overlap = carry != null && carry.text().length() + 1 + unit.text().length() <= budget;
                    if (overlap) {
                        current.add(carry);
                    }
                }
                current.add(unit);
            }
        }
        if (!current.isEmpty()) {
            drafts.add(new Draft(path, title, current, false, overlap));
        }
    }

    /** 다음 줄로 이어지는 짧은 머리 문장: "※ …" 이거나 콜론·이어지는 말로 끝나고, 문장이 끝나지 않은 줄 */
    private static boolean isLeadIn(Unit unit) {
        String text = unit.text().strip();
        if (unit.table() || text.length() > LEAD_IN_MAX || SENTENCE_END.matcher(text).find()
                || PREDICATE_END.matcher(text).find()) {
            return false;
        }
        return text.startsWith("※") || text.endsWith(":") || CONTINUES.matcher(text).find()
                || LIST_CONTINUES.matcher(text).find();
    }

    /**
     * 짧은 조각을 합친다 (표 조각은 그대로).
     * <ul>
     *     <li>본문이 {@value #TINY_BODY}자 미만이고 문장으로 끝나지 않으며 다음 조각이 다른 절이면 다음 절 첫 조각 앞에 붙인다 (표지·간지).
     *         적용 대상이 바뀌는 곳({@link #sameScope})은 넘지 않는다</li>
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
                        && !sameSection(next, draft) && !endsSentence(draft)
                        && sameScope(draft.path(), next.path())) {
                    Draft combined = next.withUnits(concat(draft.units(), next.units()), false);
                    if (combined.contentLength() <= MAX) {
                        queue.set(i + 1, combined);
                        continue;
                    }
                }
                if (body < MIN_BODY) {
                    if (next != null && !next.structuredTable() && sameSection(next, draft)) {
                        Draft combined = draft.withUnits(concat(draft.units(), next.withoutOverlap()), draft.overlap());
                        if (combined.contentLength() <= MAX) {
                            queue.set(i + 1, combined);
                            continue;
                        }
                    }
                    Draft previous = merged.isEmpty() ? null : merged.getLast();
                    if (previous != null && !previous.structuredTable()
                            && sameSection(previous, draft)) {
                        Draft combined = previous.withUnits(concat(previous.units(), draft.withoutOverlap()),
                                previous.overlap());
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

    /**
     * 같은 부모 제목 아래의 이웃한 형제 절을 한 조각으로 묶는다 (표 조각은 그대로).
     * <ul>
     *     <li>바로 위 부모 경로가 같아야 한다. 부모가 없거나(문서 이름) 최상위 제목(장)뿐이면 묶지 않는다.
     *         묶음 문서는 표제 아래 한 단계(절)뿐이어도 묶지 않는다 (지역이 섞이지 않게)</li>
     *     <li>합쳐서 목표 {@value #TARGET}자 이하, 또는 한쪽 본문이 {@value #MIN_BODY}자 미만이고 합쳐서 {@value #MAX}자 이하</li>
     *     <li>합친 조각은 {@value #MERGE_MAX_PAGES}쪽 안 (출처 쪽 범위가 넓어지지 않게)</li>
     *     <li>첫 줄은 부모 경로, 하위 제목은 본문 안에 줄로 남긴다. 같은 절이 길어 나뉜 다음 조각은 겹침을 빼고 잇는다</li>
     * </ul>
     */
    private static List<Draft> mergeSiblings(List<Draft> drafts, String fallbackTitle) {
        List<Draft> merged = new ArrayList<>(drafts.size());
        for (Draft draft : drafts) {
            Draft previous = merged.isEmpty() ? null : merged.getLast();
            Draft combined = previous == null ? null : combineSiblings(previous, draft, fallbackTitle);
            if (combined != null) {
                merged.set(merged.size() - 1, combined);
            } else {
                merged.add(draft);
            }
        }
        return merged;
    }

    private static Draft combineSiblings(Draft previous, Draft draft, String fallbackTitle) {
        if (previous.structuredTable() || draft.structuredTable() || draft.group()) {
            return null;
        }
        // 길어서 나뉜 같은 절의 조각끼리는 다시 묶지 않는다
        if (!previous.group() && previous.path().equals(draft.path())) {
            return null;
        }
        boolean sameSection = previous.group() && draft.path().equals(previous.lastPath());
        HeadingPath parent = previous.group() ? previous.path() : previous.path().parent();
        if (parent == null || parent.size() < (parent.bundled() ? 3 : 2)
                || !sameSection && !parent.equals(draft.path().parent())) {
            return null;
        }
        List<Unit> units = new ArrayList<>();
        if (previous.group()) {
            units.addAll(previous.units());
        } else {
            units.add(headingUnit(previous.path(), parent, previous.pageStart(), previous.continuation()));
            units.addAll(previous.units());
        }
        if (sameSection) {
            units.addAll(draft.withoutOverlap());
        } else {
            units.add(headingUnit(draft.path(), parent, draft.pageStart(), draft.continuation()));
            units.addAll(draft.units());
        }
        Draft combined = new Draft(parent, title(parent, fallbackTitle), units, false, previous.overlap(), true,
                draft.path(), previous.continuation());
        int length = combined.contentLength();
        boolean small = previous.bodyLength() < MIN_BODY || draft.bodyLength() < MIN_BODY;
        boolean fits = length <= TARGET || (small && length <= MAX);
        int pages = combined.pageEnd() - combined.pageStart() + 1;
        return fits && pages <= MERGE_MAX_PAGES ? combined : null;
    }

    /** 짧은 조각을 다음 절로 넘겨도 적용 대상이 같은지: 같은 묶음 문서·절·지역, 같은 시간제취업 칸 안팎 */
    private static boolean sameScope(HeadingPath from, HeadingPath to) {
        if (from.bundled() != to.bundled()
                || from.texts().contains(PartTimeRow.TITLE) != to.texts().contains(PartTimeRow.TITLE)) {
            return false;
        }
        if (!from.bundled()) {
            return true;
        }
        int shared = Math.min(BUNDLED_TOP_LEVELS, from.size());
        return to.size() >= shared && from.texts().subList(0, shared).equals(to.texts().subList(0, shared));
    }

    /** 앞 조각에서 이어지는 꼬리를 묶을 때 하위 제목 줄 끝에 붙인다 (꼬리 내용을 그 제목의 첫 내용으로 읽지 않게) */
    static final String CONTINUED = " (이어서)";

    /** 묶은 조각 안의 하위 제목 줄: 부모 경로 아래 부분. 절의 꼬리면 "… (이어서)" */
    private static Unit headingUnit(HeadingPath path, HeadingPath parent, int page, boolean continuation) {
        String text = String.join(" > ", path.texts().subList(parent.size(), path.size()));
        return new Unit(continuation ? text + CONTINUED : text, page, page, false);
    }

    /** 같은 절의 조각 (제목 경로와 첫 줄 제목이 같다. 표지 절과 첫 제목 전 내용은 경로가 둘 다 비어 있다) */
    private static boolean sameSection(Draft a, Draft b) {
        return Objects.equals(a.path(), b.path()) && Objects.equals(a.title(), b.title());
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
