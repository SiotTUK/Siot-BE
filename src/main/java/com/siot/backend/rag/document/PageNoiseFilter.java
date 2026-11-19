package com.siot.backend.rag.document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 쪽마다 반복되는 머리말·꼬리말 줄과 쪽 번호 줄을 뺀다 (검색 조각에 섞이지 않게).
 * <ul>
 *     <li>쪽 번호: 쪽의 위·아래 끝 줄이 "12", "- 12 -", "12 / 40", "p. 12", "Page 12" 모양이면 뺀다</li>
 *     <li>머리말·꼬리말: 쪽의 위·아래 {@value #EDGE_LINES}줄 안에 있고, 숫자를 무시하면 같은 줄이
 *         전체 쪽·홀수 쪽·짝수 쪽 중 어느 한 묶음의 절반 이상(최소 {@value #MIN_REPEAT_PAGES}쪽)에 나오면 뺀다.
 *         책자는 왼쪽(짝수)·오른쪽(홀수) 쪽의 머리말이 다르다</li>
 *     <li>본문 줄과 같은 높이에 있어 한 줄로 붙어 나온 머리말·꼬리말("… ☎310-2247 10 어서와~ 시흥은 처음이지?")은
 *         위·아래 끝 줄의 앞뒤에서 잘라낸다</li>
 * </ul>
 * 본문 중간의 줄은 건드리지 않는다.
 */
public final class PageNoiseFilter {

    static final int EDGE_LINES = 2;
    static final int MIN_REPEAT_PAGES = 3;
    /** 이보다 짧은 반복 줄은 다른 줄의 앞뒤에서 잘라내지 않는다 (본문 일부를 지우지 않게) */
    static final int MIN_AFFIX_LENGTH = 6;

    private static final Pattern PAGE_NUMBER = Pattern.compile(
            "^(?:[-–—]\\s*)?\\d{1,4}(?:\\s*[-–—])?$"
                    + "|^\\d{1,4}\\s*/\\s*\\d{1,4}$"
                    + "|^(?i:p\\.?|page)\\s*\\d{1,4}$"
                    + "|^[\\[(]\\s*\\d{1,4}\\s*[])]$");
    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private PageNoiseFilter() {
    }

    public static List<PageText> clean(List<PageText> pages) {
        List<List<String>> linesPerPage = new ArrayList<>(pages.size());
        for (PageText page : pages) {
            linesPerPage.add(List.of(page.text().split("\n", -1)));
        }
        Set<String> repeated = new HashSet<>(repeatedEdgeLines(linesPerPage));
        repeated.addAll(repeatedEdgeLines(byParity(pages, linesPerPage, 1)));
        repeated.addAll(repeatedEdgeLines(byParity(pages, linesPerPage, 0)));
        List<Pattern> affixes = affixPatterns(repeated);

        List<PageText> cleaned = new ArrayList<>(pages.size());
        for (int i = 0; i < pages.size(); i++) {
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
                    if (line.isBlank() || repeated.contains(normalize(line.strip()))) {
                        continue;
                    }
                }
                text.append(line).append('\n');
            }
            cleaned.add(new PageText(pages.get(i).page(), text.toString().strip()));
        }
        return cleaned;
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

    private static Set<String> repeatedEdgeLines(List<List<String>> linesPerPage) {
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
        int threshold = Math.max(MIN_REPEAT_PAGES, (linesPerPage.size() + 1) / 2);
        Set<String> repeated = new HashSet<>();
        pageCounts.forEach((line, count) -> {
            if (count >= threshold) {
                repeated.add(line);
            }
        });
        return repeated;
    }

    /** 반복 줄이 다른 줄의 앞이나 뒤에 붙어 있는 모양 (숫자는 아무 숫자, 공백은 아무 공백) */
    private static List<Pattern> affixPatterns(Set<String> repeated) {
        List<Pattern> patterns = new ArrayList<>();
        for (String line : repeated) {
            if (line.replace("#", "").strip().length() < MIN_AFFIX_LENGTH) {
                continue;
            }
            StringBuilder regex = new StringBuilder();
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

    /** 비어 있지 않은 줄 중 위·아래 끝 EDGE_LINES 줄의 위치 */
    private static Set<Integer> edgeIndexes(List<String> lines) {
        List<Integer> nonEmpty = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).isBlank()) {
                nonEmpty.add(i);
            }
        }
        Set<Integer> edges = new HashSet<>();
        for (int i = 0; i < Math.min(EDGE_LINES, nonEmpty.size()); i++) {
            edges.add(nonEmpty.get(i));
            edges.add(nonEmpty.get(nonEmpty.size() - 1 - i));
        }
        return edges;
    }

    /** 쪽 번호·날짜처럼 바뀌는 숫자는 같게 본다 */
    private static String normalize(String line) {
        return SPACES.matcher(DIGITS.matcher(line).replaceAll("#")).replaceAll(" ");
    }
}
