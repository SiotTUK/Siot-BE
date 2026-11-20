package com.siot.backend.event.collect;

import java.util.Locale;
import java.util.regex.Pattern;

/** 출처 간 같은 행사 판단용 행사명 정규화 ("제21회 시흥갯골축제" = "2026 시흥갯골축제" = "시흥갯골축제") */
public final class EventNames {

    private static final Pattern ROUND = Pattern.compile("제\\s*\\d+\\s*회");
    private static final Pattern YEAR = Pattern.compile("(?<!\\d)(?:19|20)\\d{2}\\s*년?(?!\\d)");
    private static final Pattern NOT_LETTER_OR_DIGIT = Pattern.compile("[^\\p{L}\\p{N}]");

    private EventNames() {
    }

    public static String normalize(String title) {
        if (title == null) {
            return "";
        }
        String text = ROUND.matcher(title).replaceAll("");
        text = YEAR.matcher(text).replaceAll("");
        return NOT_LETTER_OR_DIGIT.matcher(text).replaceAll("").toLowerCase(Locale.ROOT);
    }
}
