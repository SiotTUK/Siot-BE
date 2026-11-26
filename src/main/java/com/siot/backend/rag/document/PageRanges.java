package com.siot.backend.rag.document;

import java.util.Collections;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 쪽 범위 "1-7", "1-3,5" (PDF 뷰어 기준 1부터). 적재에서 뺄 쪽(표지·목차 등)을 지정할 때 쓴다.
 */
public final class PageRanges {

    /** 한 번에 지정할 수 있는 최대 쪽 번호 (잘못된 입력으로 큰 집합을 만들지 않게) */
    static final int MAX_PAGE = 10_000;
    private static final Pattern PART = Pattern.compile("^(\\d{1,5})(?:\\s*-\\s*(\\d{1,5}))?$");

    private PageRanges() {
    }

    /**
     * @return 쪽 번호 (오름차순). null 이거나 비어 있으면 빈 집합
     * @throws IllegalArgumentException 형식이 틀림 (0쪽, 끝이 시작보다 작음, 숫자가 아님 등)
     */
    public static SortedSet<Integer> parse(String spec) {
        if (spec == null || spec.isBlank()) {
            return Collections.emptySortedSet();
        }
        SortedSet<Integer> pages = new TreeSet<>();
        for (String part : spec.split(",")) {
            Matcher matcher = PART.matcher(part.strip());
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Invalid page range: " + part.strip());
            }
            int start = Integer.parseInt(matcher.group(1));
            int end = matcher.group(2) == null ? start : Integer.parseInt(matcher.group(2));
            if (start < 1 || end < start || end > MAX_PAGE) {
                throw new IllegalArgumentException("Invalid page range: " + part.strip());
            }
            for (int page = start; page <= end; page++) {
                pages.add(page);
            }
        }
        return pages;
    }

    /** 이어지는 쪽은 묶어서 "1-3,5" 로 */
    public static String format(Set<Integer> pages) {
        StringBuilder out = new StringBuilder();
        Integer start = null;
        Integer previous = null;
        for (int page : new TreeSet<>(pages)) {
            if (previous != null && page == previous + 1) {
                previous = page;
                continue;
            }
            appendRange(out, start, previous);
            start = page;
            previous = page;
        }
        appendRange(out, start, previous);
        return out.toString();
    }

    private static void appendRange(StringBuilder out, Integer start, Integer end) {
        if (start == null) {
            return;
        }
        if (!out.isEmpty()) {
            out.append(',');
        }
        out.append(start);
        if (!start.equals(end)) {
            out.append('-').append(end);
        }
    }
}
