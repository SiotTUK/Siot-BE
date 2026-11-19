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
 *         전체 쪽의 절반 이상(최소 {@value #MIN_REPEAT_PAGES}쪽)에 나오면 뺀다</li>
 * </ul>
 * 본문 중간의 줄은 건드리지 않는다.
 */
public final class PageNoiseFilter {

    static final int EDGE_LINES = 2;
    static final int MIN_REPEAT_PAGES = 3;

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
        Set<String> repeated = repeatedEdgeLines(linesPerPage);

        List<PageText> cleaned = new ArrayList<>(pages.size());
        for (int i = 0; i < pages.size(); i++) {
            List<String> lines = linesPerPage.get(i);
            Set<Integer> edges = edgeIndexes(lines);
            StringBuilder text = new StringBuilder();
            for (int j = 0; j < lines.size(); j++) {
                String line = lines.get(j).strip();
                if (edges.contains(j) && (PAGE_NUMBER.matcher(line).matches() || repeated.contains(normalize(line)))) {
                    continue;
                }
                text.append(lines.get(j)).append('\n');
            }
            cleaned.add(new PageText(pages.get(i).page(), text.toString().strip()));
        }
        return cleaned;
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
