package com.siot.backend.rag.document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 쪽마다 반복되는 머리말·꼬리말 줄과 쪽 번호 줄을 뺀다 (검색 조각에 섞이지 않게).
 * <ul>
 *     <li>쪽 번호: 쪽의 위·아래 끝 줄이 "12", "- 12 -", "12 / 40", "p. 12", "Page 12" 모양이면 뺀다</li>
 *     <li>머리말·꼬리말: 쪽의 위·아래 끝 줄(보통 {@value #EDGE_LINES}줄, 줄이 많은 쪽은 {@value #WIDE_EDGE_LINES}줄) 안에 있고,
 *         숫자와 사용자 영역 문자(아이콘)를 무시하면 같은 줄이 전체 쪽의 {@value #MIN_REPEAT_PERCENT}% 이상,
 *         또는 홀수 쪽·짝수 쪽 중 한 묶음의 절반 이상에 나오면 뺀다 (최소 {@value #MIN_REPEAT_PAGES}쪽).
 *         책자는 왼쪽(짝수)·오른쪽(홀수) 쪽의 머리말이 다르다</li>
 *     <li>본문 줄과 같은 높이에 있어 한 줄로 붙어 나온 머리말·꼬리말("… ☎310-2247 10 어서와~ 시흥은 처음이지?")은
 *         위·아래 끝 줄의 앞뒤에서 잘라낸다. 아이콘이 붙은 반복 줄("󰁾 목차")은 짧아도 잘라낸다</li>
 *     <li>아이콘 링크: 사용자 영역 문자 아이콘 뒤의 짧은 글자("󰁾 목차")가 전체 쪽의 {@value #MIN_LINK_PERCENT}% 이상에 나오고,
 *         그 아이콘이 쓰인 곳의 {@value #MIN_LINK_SHARE_PERCENT}% 이상이 이 글자이면 쪽 어디에 있든 지운다
 *         (절 제목 옆에 붙은 "목차로 돌아가기" 링크). 여러 제목 앞에 기호로 쓰인 아이콘("&#xF000; 외국인 …")은 남는다</li>
 * </ul>
 * 그 밖의 본문 중간 줄은 건드리지 않는다.
 */
public final class PageNoiseFilter {

    static final int EDGE_LINES = 2;
    static final int WIDE_EDGE_LINES = 3;
    /** 이 줄 수 이상인 쪽은 위·아래 {@value #WIDE_EDGE_LINES}줄을 본다 (가운데 줄이 끝 줄에 들지 않게) */
    static final int WIDE_EDGE_MIN_LINES = 7;
    static final int MIN_REPEAT_PAGES = 3;
    static final int MIN_REPEAT_PERCENT = 30;
    /** 이보다 짧은 반복 줄은 다른 줄의 앞뒤에서 잘라내지 않는다 (본문 일부를 지우지 않게). 아이콘 줄은 예외 */
    static final int MIN_AFFIX_LENGTH = 6;

    private static final Pattern PAGE_NUMBER = Pattern.compile(
            "^(?:[-–—]\\s*)?\\d{1,4}(?:\\s*[-–—])?$"
                    + "|^\\d{1,4}\\s*/\\s*\\d{1,4}$"
                    + "|^(?i:p\\.?|page)\\s*\\d{1,4}$"
                    + "|^[\\[(]\\s*\\d{1,4}\\s*[])]$");
    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    /** 사용자 영역 문자 (글꼴 아이콘): U+E000~U+F8FF, U+F0000 이상 */
    static final String PRIVATE_USE = "[\\x{E000}-\\x{F8FF}\\x{F0000}-\\x{10FFFF}]";
    private static final Pattern PRIVATE_USE_CHARS = Pattern.compile(PRIVATE_USE + "+");
    /** 아이콘 + 짧은 글자 ("󰁾 목차") */
    private static final Pattern ICON_LINK = Pattern.compile("(" + PRIVATE_USE + "+)\\s*([\\p{L}]{1,6})(?![\\p{L}\\d])");
    /** 아이콘 링크로 볼 최소 쪽 비율 (절마다 붙는 링크라 쪽마다 있지는 않다) */
    static final int MIN_LINK_PERCENT = 10;
    /** 그 아이콘이 쓰인 곳 중 이 글자와 짝인 비율 */
    static final int MIN_LINK_SHARE_PERCENT = 60;

    private PageNoiseFilter() {
    }

    public static List<PageText> clean(List<PageText> pages) {
        List<PageText> withoutLinks = removeIconLinks(pages);
        List<List<String>> linesPerPage = new ArrayList<>(withoutLinks.size());
        for (PageText page : withoutLinks) {
            linesPerPage.add(List.of(page.text().split("\n", -1)));
        }
        Set<String> repeated = repeatedLines(withoutLinks, linesPerPage);
        List<Pattern> affixes = affixPatterns(repeated, iconLines(linesPerPage, repeated));

        List<PageText> cleaned = new ArrayList<>(withoutLinks.size());
        for (int i = 0; i < withoutLinks.size(); i++) {
            List<String> lines = linesPerPage.get(i);
            Set<Integer> edges = edgeIndexes(lines);
            StringBuilder text = new StringBuilder();
            for (int j = 0; j < lines.size(); j++) {
                String line = lines.get(j);
                if (edges.contains(j)) {
                    String stripped = line.strip();
                    if (PAGE_NUMBER.matcher(stripped).matches() || repeated.contains(normalize(stripped))) {
                        continue;
                    }
                    line = removeAffixes(line, affixes);
                    if (line.isBlank() || repeated.contains(normalize(line.strip()))
                            || PAGE_NUMBER.matcher(line.strip()).matches()) {
                        continue;
                    }
                }
                text.append(line).append('\n');
            }
            cleaned.add(withoutLinks.get(i).withText(text.toString().strip()));
        }
        return cleaned;
    }

    /**
     * 머리말·꼬리말로 볼 줄 ({@link #normalize} 한 모양). 태그 PDF 처럼 구조 트리에서 꺼낸 줄을 거를 때도 쓴다.
     */
    public static Set<String> repeatedLines(List<PageText> pages) {
        List<PageText> withoutLinks = removeIconLinks(pages);
        List<List<String>> linesPerPage = new ArrayList<>(withoutLinks.size());
        for (PageText page : withoutLinks) {
            linesPerPage.add(List.of(page.text().split("\n", -1)));
        }
        return repeatedLines(withoutLinks, linesPerPage);
    }

    /**
     * 구조 트리에서 꺼낸 줄이 반복 머리말·꼬리말과 같은지 (숫자·공백·아이콘 무시).
     * 태그 PDF 는 쪽 번호와 머리말이 다른 줄로 나뉘기도 하므로 숫자를 지우고 비교한다 ("28 비자 내비게이터" / "비자 내비게이터").
     */
    public static boolean isRepeatedLine(String line, Set<String> repeated) {
        String key = loose(normalize(line.strip()));
        if (key.isEmpty()) {
            return false;
        }
        for (String candidate : repeated) {
            if (key.equals(loose(candidate))) {
                return true;
            }
        }
        return false;
    }

    private static String loose(String normalized) {
        return normalized.replace("#", "").replace(" ", "");
    }

    private static Set<String> repeatedLines(List<PageText> pages, List<List<String>> linesPerPage) {
        Set<String> repeated = new HashSet<>(repeatedEdgeLines(linesPerPage, allPagesThreshold(linesPerPage.size())));
        List<List<String>> odd = byParity(pages, linesPerPage, 1);
        List<List<String>> even = byParity(pages, linesPerPage, 0);
        repeated.addAll(repeatedEdgeLines(odd, halfThreshold(odd.size())));
        repeated.addAll(repeatedEdgeLines(even, halfThreshold(even.size())));
        return repeated;
    }

    private static int allPagesThreshold(int pageCount) {
        return Math.max(MIN_REPEAT_PAGES, (pageCount * MIN_REPEAT_PERCENT + 99) / 100);
    }

    private static int halfThreshold(int pageCount) {
        return Math.max(MIN_REPEAT_PAGES, (pageCount + 1) / 2);
    }

    /** 아이콘 + 짧은 글자가 전체 쪽의 30% 이상에 나오면 모든 쪽에서 지운다 */
    private static List<PageText> removeIconLinks(List<PageText> pages) {
        Map<String, Integer> pageCounts = new HashMap<>();
        Map<String, Integer> pairCounts = new HashMap<>();
        Map<String, Integer> iconCounts = new HashMap<>();
        for (PageText page : pages) {
            Set<String> seen = new HashSet<>();
            Matcher matcher = ICON_LINK.matcher(page.text());
            while (matcher.find()) {
                String pair = linkKey(matcher);
                pairCounts.merge(pair, 1, Integer::sum);
                if (seen.add(pair)) {
                    pageCounts.merge(pair, 1, Integer::sum);
                }
            }
            Matcher icons = PRIVATE_USE_CHARS.matcher(page.text());
            while (icons.find()) {
                iconCounts.merge(icons.group(), 1, Integer::sum);
            }
        }
        int threshold = Math.max(MIN_REPEAT_PAGES, (pages.size() * MIN_LINK_PERCENT + 99) / 100);
        Set<String> links = new HashSet<>();
        pageCounts.forEach((pair, count) -> {
            String icon = pair.substring(0, pair.indexOf('\u0000'));
            // 아이콘이 거의 이 글자하고만 쓰임 (기호로 여러 제목 앞에 쓰인 아이콘은 아니다)
            if (count >= threshold && pairCounts.get(pair) * 100 >= iconCounts.getOrDefault(icon, 0) * MIN_LINK_SHARE_PERCENT) {
                links.add(pair);
            }
        });
        if (links.isEmpty()) {
            return pages;
        }
        List<PageText> cleaned = new ArrayList<>(pages.size());
        for (PageText page : pages) {
            // 줄 모양은 링크를 지운 줄로도 찾을 수 있게 한다
            Map<String, PageText.LineStyle> styles = new HashMap<>(page.styles());
            page.styles().forEach((line, style) ->
                    styles.putIfAbsent(PdfTextExtractor.styleKey(removeLinks(line, links)), style));
            cleaned.add(new PageText(page.page(), removeLinks(page.text(), links), styles));
        }
        return cleaned;
    }

    private static String removeLinks(String text, Set<String> links) {
        Matcher matcher = ICON_LINK.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, links.contains(linkKey(matcher)) ? "" : Matcher.quoteReplacement(matcher.group()));
        }
        matcher.appendTail(result);
        return removeSeparatedLinks(result.toString(), links);
    }

    /**
     * 화면 위치 순으로 읽으면 아이콘과 글자가 떨어지기도 한다 ("󰁾 상부 추천서 … 또는 목차 고유번호증").
     * 링크로 확인된 문서에서는 남은 링크 아이콘과, 앞뒤가 공백인 링크 글자("목차")를 모두 지운다.
     */
    private static String removeSeparatedLinks(String text, Set<String> links) {
        String result = text;
        for (String pair : links) {
            int split = pair.indexOf('\u0000');
            result = result.replace(pair.substring(0, split), "");
            // 글자 뒤의 공백도 함께 지운다 ("또는 목차 고유번호증" → "또는 고유번호증")
            result = result.replaceAll("(?m)(^|[ \\t])" + Pattern.quote(pair.substring(split + 1)) + "(?:[ \\t]+|$)", "$1");
        }
        return result;
    }

    /** 아이콘 + 구분자 + 글자 */
    private static String linkKey(Matcher matcher) {
        return matcher.group(1) + '\u0000' + matcher.group(2);
    }

    /** 쪽 번호가 홀수(1) 또는 짝수(0)인 쪽의 줄 */
    private static List<List<String>> byParity(List<PageText> pages, List<List<String>> linesPerPage, int parity) {
        List<List<String>> selected = new ArrayList<>();
        for (int i = 0; i < pages.size(); i++) {
            if (pages.get(i).page() % 2 == parity) {
                selected.add(linesPerPage.get(i));
            }
        }
        return selected;
    }

    private static Set<String> repeatedEdgeLines(List<List<String>> linesPerPage, int threshold) {
        Map<String, Integer> pageCounts = new HashMap<>();
        for (List<String> lines : linesPerPage) {
            Set<String> seen = new HashSet<>();
            for (int index : edgeIndexes(lines)) {
                String key = normalize(lines.get(index).strip());
                if (!key.isEmpty() && seen.add(key)) {
                    pageCounts.merge(key, 1, Integer::sum);
                }
            }
        }
        Set<String> repeated = new HashSet<>();
        pageCounts.forEach((line, count) -> {
            if (count >= threshold) {
                repeated.add(line);
            }
        });
        return repeated;
    }

    /** 반복 줄 중 원래 아이콘이 붙어 있던 줄 (짧아도 앞뒤에서 잘라낸다) */
    private static Set<String> iconLines(List<List<String>> linesPerPage, Set<String> repeated) {
        Set<String> icons = new HashSet<>();
        for (List<String> lines : linesPerPage) {
            for (int index : edgeIndexes(lines)) {
                String line = lines.get(index).strip();
                if (PRIVATE_USE_CHARS.matcher(line).find()) {
                    String key = normalize(line);
                    if (repeated.contains(key)) {
                        icons.add(key);
                    }
                }
            }
        }
        return icons;
    }

    /** 반복 줄이 다른 줄의 앞이나 뒤에 붙어 있는 모양 (숫자는 아무 숫자, 공백은 아무 공백, 아이콘은 있어도 없어도) */
    private static List<Pattern> affixPatterns(Set<String> repeated, Set<String> iconLines) {
        List<Pattern> patterns = new ArrayList<>();
        for (String line : repeated) {
            boolean icon = iconLines.contains(line);
            if (!icon && line.replace("#", "").strip().length() < MIN_AFFIX_LENGTH) {
                continue;
            }
            StringBuilder regex = new StringBuilder(PRIVATE_USE + "*\\s*");
            for (char c : line.toCharArray()) {
                if (c == '#') {
                    regex.append("\\d+");
                } else if (c == ' ') {
                    regex.append("\\s+");
                } else {
                    regex.append(Pattern.quote(String.valueOf(c)));
                }
            }
            patterns.add(Pattern.compile("\\s+" + regex + "\\s*$"));
            patterns.add(Pattern.compile("^\\s*" + regex + "\\s+"));
        }
        return patterns;
    }

    private static String removeAffixes(String line, List<Pattern> affixes) {
        String result = line;
        for (Pattern affix : affixes) {
            result = affix.matcher(result).replaceFirst("");
        }
        return result;
    }

    /** 비어 있지 않은 줄 중 위·아래 끝 줄의 위치 (줄이 많은 쪽은 3줄, 아니면 2줄) */
    private static Set<Integer> edgeIndexes(List<String> lines) {
        List<Integer> nonEmpty = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).isBlank()) {
                nonEmpty.add(i);
            }
        }
        int edgeLines = nonEmpty.size() >= WIDE_EDGE_MIN_LINES ? WIDE_EDGE_LINES : EDGE_LINES;
        Set<Integer> edges = new HashSet<>();
        for (int i = 0; i < Math.min(edgeLines, nonEmpty.size()); i++) {
            edges.add(nonEmpty.get(i));
            edges.add(nonEmpty.get(nonEmpty.size() - 1 - i));
        }
        return edges;
    }

    /** 쪽 번호·날짜처럼 바뀌는 숫자는 같게 보고, 아이콘(사용자 영역 문자)은 지운다 */
    static String normalize(String line) {
        String withoutIcons = PRIVATE_USE_CHARS.matcher(line).replaceAll(" ").strip();
        return SPACES.matcher(DIGITS.matcher(withoutIcons).replaceAll("#")).replaceAll(" ");
    }
}
