package com.siot.backend.event.domain;

import com.siot.backend.global.common.SupportedLanguages;

import java.util.Map;

/**
 * 행사 카테고리 (EVE-10 필터, CB-10). 출처 분류를 그대로 옮길 수 있게 정했다 (2026-11-26).
 * <pre>
 *   코드          지지씨 category   TourAPI lclsSystm2   전국문화축제표준데이터
 *   FESTIVAL     -                EV01 (축제)           전부
 *   PERFORMANCE  공연              EV02 (공연)           -
 *   EXHIBITION   전시              -                     -
 *   EDUCATION    교육              -                     -
 *   EVENT        행사              EV03 (행사)           -
 * </pre>
 * 출처에 없거나 모르는 값은 EVENT. 관리자가 등록하는 한국어 강좌 등은 EDUCATION (EVE-05·07).
 * 다문화 여부는 카테고리가 아니라 event.is_multicultural 로 따로 둔다.
 */
public enum EventCategory {

    FESTIVAL("축제", "Festival", "节庆", "Lễ hội"),
    PERFORMANCE("공연", "Performance", "演出", "Biểu diễn"),
    EXHIBITION("전시", "Exhibition", "展览", "Triển lãm"),
    EDUCATION("교육·체험", "Education & experience", "教育·体验", "Giáo dục & trải nghiệm"),
    EVENT("행사", "Event", "活动", "Sự kiện");

    /** 요청 DTO 의 {@code @Pattern} 용 */
    public static final String PATTERN = "FESTIVAL|PERFORMANCE|EXHIBITION|EDUCATION|EVENT";

    private final Map<String, String> names;

    EventCategory(String ko, String en, String zh, String vi) {
        this.names = Map.of("ko", ko, "en", en, "zh", zh, "vi", vi);
    }

    /** 화면 언어의 이름 (지원하지 않는 언어면 한국어) */
    public String displayName(String language) {
        return names.get(SupportedLanguages.orDefault(language));
    }

    /** 경기문화재단 지지씨 API 의 category (공연·전시·행사·교육) */
    public static EventCategory fromGgc(String category) {
        if (category == null) {
            return EVENT;
        }
        return switch (category.strip()) {
            case "공연" -> PERFORMANCE;
            case "전시" -> EXHIBITION;
            case "교육" -> EDUCATION;
            default -> EVENT; // "행사" 와 모르는 값
        };
    }

    /**
     * 한국관광공사 TourAPI 분류. 신분류 lclsSystm2 (EV01 축제·EV02 공연·EV03 행사) 를 먼저 보고,
     * 비어 있으면 옛 분류 cat2 (A0207 축제) 를 본다. 옛 cat2 A0208(공연/행사)은 둘이 섞여 있어 EVENT.
     */
    public static EventCategory fromTourApi(String lclsSystm2, String cat2) {
        if (lclsSystm2 != null && !lclsSystm2.isBlank()) {
            return switch (lclsSystm2.strip()) {
                case "EV01" -> FESTIVAL;
                case "EV02" -> PERFORMANCE;
                default -> EVENT;
            };
        }
        return "A0207".equals(cat2 == null ? null : cat2.strip()) ? FESTIVAL : EVENT;
    }

    /** 요청 값 (대소문자 무시). null 이나 빈 값이면 null */
    public static EventCategory parse(String value) {
        return value == null || value.isBlank() ? null : valueOf(value.strip().toUpperCase(java.util.Locale.ROOT));
    }
}
