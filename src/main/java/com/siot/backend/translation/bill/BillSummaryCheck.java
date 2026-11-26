package com.siot.backend.translation.bill;

import com.siot.backend.translation.BillReading;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * RUL-07: AI 요약의 금액·날짜가 AI 가 뽑은 amount·dueDate 와 같은지 확인한다.
 * 다르면 요약을 버리고 서버가 amount·dueDate 로 문장을 만든다 ({@link BillMessages#summary}).
 * <ul>
 *     <li>요약의 날짜는 dueDate·lateDueDate 중 하나여야 하고, dueDate 가 있으면 "언제까지" 줄에 있어야 한다</li>
 *     <li>요약의 금액(1,000 이상)은 amount·lateAmount·penalty 중 하나여야 하고, amount 가 있으면 "얼마" 줄에 있어야 한다</li>
 * </ul>
 */
final class BillSummaryCheck {

    /** 2026-07-31, 2026.7.31, 2026/07/31, 2026년 7월 31일 */
    private static final Pattern DATE =
            Pattern.compile("(?<!\\d)(\\d{4})\\s*[-./년]\\s*(\\d{1,2})\\s*[-./월]\\s*(\\d{1,2})(?!\\d)\\s*일?");
    /** 전화번호 (금액으로 세지 않는다) */
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\d{2,4}-)?\\d{3,4}-\\d{4}(?!\\d)");
    /** 123,450 / 123.450 (베트남어) / 123450 */
    private static final Pattern MONEY = Pattern.compile("(?<![\\d,.])\\d{1,3}(?:[,.]\\d{3})+(?![\\d])|(?<![\\d,.])\\d{4,}(?![\\d])");

    private BillSummaryCheck() {
    }

    static boolean consistent(BillReading reading) {
        BillReading.Summary summary = reading.summary();
        if (summary == null || Stream.of(summary.what(), summary.due(), summary.amount(), summary.howToPay())
                .anyMatch(line -> line == null || line.isBlank())) {
            return false;
        }
        String all = String.join("\n", summary.what(), summary.due(), summary.amount(), summary.howToPay());

        Set<LocalDate> allowedDates = nonNull(reading.dueDate(), reading.lateDueDate());
        Set<LocalDate> dates = dates(all);
        if (dates == null || !allowedDates.containsAll(dates)) {
            return false;
        }
        if (reading.dueDate() != null && !Objects.requireNonNullElse(dates(summary.due()), Set.of())
                .contains(reading.dueDate())) {
            return false;
        }

        Set<Long> allowedAmounts = nonNull(reading.amount(), reading.lateAmount(), reading.penalty());
        if (!allowedAmounts.containsAll(amounts(all))) {
            return false;
        }
        return reading.amount() == null || amounts(summary.amount()).contains(reading.amount());
    }

    /** @return 날짜들. 날짜 모양인데 없는 날짜(2월 30일)면 null */
    private static Set<LocalDate> dates(String text) {
        Set<LocalDate> result = new HashSet<>();
        Matcher matcher = DATE.matcher(text);
        while (matcher.find()) {
            try {
                result.add(LocalDate.of(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
                        Integer.parseInt(matcher.group(3))));
            } catch (DateTimeException e) {
                return null;
            }
        }
        return result;
    }

    /** 날짜·전화번호를 뺀 뒤 1,000 이상의 수. 구분자 없는 1900~2100 은 연도로 보고 뺀다 */
    private static Set<Long> amounts(String text) {
        String rest = PHONE.matcher(DATE.matcher(text).replaceAll(" ")).replaceAll(" ");
        Set<Long> result = new HashSet<>();
        Matcher matcher = MONEY.matcher(rest);
        while (matcher.find()) {
            String raw = matcher.group();
            String digits = raw.replaceAll("[,.]", "");
            if (digits.length() > 15) {
                result.add(-1L);
                continue;
            }
            long value = Long.parseLong(digits);
            boolean year = raw.equals(digits) && value >= 1900 && value <= 2100;
            if (!year && value >= 1000) {
                result.add(value);
            }
        }
        return result;
    }

    @SafeVarargs
    private static <T> Set<T> nonNull(T... values) {
        Set<T> result = new HashSet<>();
        for (T value : values) {
            if (value != null) {
                result.add(value);
            }
        }
        return result;
    }
}
