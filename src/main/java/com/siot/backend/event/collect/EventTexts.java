package com.siot.backend.event.collect;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 출처 응답의 글·날짜·시각을 다듬는다 */
public final class EventTexts {

    /** event_translation.title 길이 */
    static final int TITLE_MAX = 255;

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("uuuu-MM-dd")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter BASIC = DateTimeFormatter.ofPattern("uuuuMMdd")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern SPACES = Pattern.compile("\\s+");
    /** 첫 HH:mm ("19:30 (80분)", "17:00 (러닝타임 90분)") */
    private static final Pattern FIRST_TIME = Pattern.compile("(?<!\\d)(\\d{1,2}):(\\d{2})(?!\\d)");
    /** 앞이 "HH:mm ~ HH:mm" 이면 끝 시각도 ("10:00~17:00") */
    private static final Pattern TIME_RANGE = Pattern.compile(
            "^\\s*(\\d{1,2}):(\\d{2})\\s*[~∼〜\\-–]\\s*(\\d{1,2}):(\\d{2})(?!\\d)");

    private EventTexts() {
    }

    /** 앞뒤 공백을 빼고 겹친 공백을 하나로. 비면 null */
    public static String clean(String text) {
        if (text == null) {
            return null;
        }
        String cleaned = SPACES.matcher(text.replace(' ', ' ')).replaceAll(" ").strip();
        return cleaned.isEmpty() ? null : cleaned;
    }

    /** 행사명 (최대 255자) */
    public static String title(String text) {
        String cleaned = clean(text);
        if (cleaned == null || cleaned.length() <= TITLE_MAX) {
            return cleaned;
        }
        return cleaned.substring(0, TITLE_MAX);
    }

    /** yyyy-MM-dd 만. 아니면 null */
    public static LocalDate isoDate(String text) {
        return parse(text, ISO);
    }

    /** yyyyMMdd 만. 아니면 null */
    public static LocalDate basicDate(String text) {
        return parse(text, BASIC);
    }

    private static LocalDate parse(String text, DateTimeFormatter format) {
        String cleaned = clean(text);
        if (cleaned == null) {
            return null;
        }
        try {
            return LocalDate.parse(cleaned, format);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * 자유 문장에서 시작·끝 시각을 뽑는다. 앞의 HH:mm 이 시작 시각, "10:00~17:00" 꼴이면 끝 시각도.
     *
     * @return {시작, 끝}. 못 뽑은 칸은 null
     */
    public static LocalTime[] times(String text) {
        String cleaned = clean(text);
        if (cleaned == null) {
            return new LocalTime[]{null, null};
        }
        Matcher range = TIME_RANGE.matcher(cleaned);
        if (range.find()) {
            LocalTime start = time(range.group(1), range.group(2));
            LocalTime end = time(range.group(3), range.group(4));
            if (start != null) {
                return new LocalTime[]{start, end};
            }
        }
        Matcher first = FIRST_TIME.matcher(cleaned);
        if (first.find()) {
            return new LocalTime[]{time(first.group(1), first.group(2)), null};
        }
        return new LocalTime[]{null, null};
    }

    private static LocalTime time(String hour, String minute) {
        int h = Integer.parseInt(hour);
        int m = Integer.parseInt(minute);
        return h < 24 && m < 60 ? LocalTime.of(h, m) : null;
    }
}
