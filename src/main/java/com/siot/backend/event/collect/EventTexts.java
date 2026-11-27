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

    /** 줄을 바꾸는 태그 (<br>, </p>, </div>) */
    private static final Pattern LINE_BREAK_TAG = Pattern.compile("(?i)<br\\s*/?>|</p\\s*>|</div\\s*>");
    private static final Pattern TAG = Pattern.compile("<[^>]*>");
    private static final Pattern HREF = Pattern.compile("(?i)href\\s*=\\s*[\"']?([^\"'\\s>]+)");
    private static final Pattern HTTP_URL = Pattern.compile("(?i)https?://[^\\s\"'<>]+");
    private static final Pattern WWW_URL = Pattern.compile("(?i)(?<![\\w./])www\\.[^\\s\"'<>]+");

    private EventTexts() {
    }

    /**
     * HTML 조각을 글로 바꾼다 (TourAPI overview). &lt;br&gt; 은 줄바꿈, 다른 태그는 공백, 줄마다 공백을 다듬고 빈 줄은 하나까지.
     *
     * @return 비면 null
     */
    public static String htmlToText(String html) {
        if (html == null) {
            return null;
        }
        String text = LINE_BREAK_TAG.matcher(html.replace("\r\n", "\n")).replaceAll("\n");
        text = unescapeHtml(TAG.matcher(text).replaceAll(" "));
        StringBuilder result = new StringBuilder();
        boolean blank = false;
        for (String line : text.split("\n", -1)) {
            String cleaned = clean(line);
            if (cleaned == null) {
                blank = !result.isEmpty();
                continue;
            }
            if (!result.isEmpty()) {
                result.append(blank ? "\n\n" : "\n");
            }
            result.append(cleaned);
            blank = false;
        }
        return result.isEmpty() ? null : result.toString();
    }

    /**
     * 첫 링크 (TourAPI homepage): {@code <a href="...">} 면 href, 아니면 글 안의 http(s)://, 그것도 없으면 www.로 시작하는 주소.
     *
     * @return 없으면 null
     */
    public static String firstUrl(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        Matcher href = HREF.matcher(html);
        while (href.find()) {
            String url = unescapeHtml(href.group(1)).strip();
            if (url.regionMatches(true, 0, "http://", 0, 7) || url.regionMatches(true, 0, "https://", 0, 8)) {
                return url;
            }
        }
        String text = unescapeHtml(TAG.matcher(html).replaceAll(" "));
        Matcher http = HTTP_URL.matcher(text);
        if (http.find()) {
            return http.group();
        }
        Matcher www = WWW_URL.matcher(text);
        return www.find() ? "http://" + www.group() : null;
    }

    /**
     * 사진 URL (BE #19): 앞뒤 공백을 빼고 http(s):// 로 시작하는 값만. 2000자를 넘거나 다른 값이면 null
     * (DB 제약 ck_event_thumbnail_url 과 같은 규칙. 어긋난 값 때문에 행사 저장이 실패하지 않게 한다)
     */
    public static String imageUrl(String text) {
        String url = text == null ? null : text.strip();
        if (url == null || url.isEmpty() || url.length() > 2000) {
            return null;
        }
        return url.regionMatches(true, 0, "http://", 0, 7) || url.regionMatches(true, 0, "https://", 0, 8)
                ? url : null;
    }

    private static String unescapeHtml(String text) {
        return text.replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&#039;", "'")
                .replace("&amp;", "&");
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
