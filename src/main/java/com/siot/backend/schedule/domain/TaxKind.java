package com.siot.backend.schedule.domain;

import java.util.Locale;
import java.util.Map;

/**
 * 세금 일정 종류 (CAL-03·RUL-06, BE #29). 생활수칙 세금 항목·개인 세금 일정에서 공통 세금 상세를 찾는 기준.
 * 공통 일정({@link CalendarSchedule})은 {@code kind} 가 같거나, 연결된 항목(guide)의 코드가 그 종류에 대응하면 같은 세금이다.
 */
public final class TaxKind {

    public static final String CAR = "TAX_CAR";
    public static final String PROPERTY = "TAX_PROPERTY";
    public static final String RESIDENT = "TAX_RESIDENT";

    /** 생활수칙 세금 항목 코드 → 세금 종류 */
    private static final Map<String, String> BY_GUIDE_CODE = Map.of(
            "tax-car", CAR,
            "tax-property", PROPERTY,
            "tax-resident", RESIDENT);

    private TaxKind() {
    }

    /** @return 세금 종류, 세금 항목이 아니면 null */
    public static String forGuideCode(String guideCode) {
        return guideCode == null ? null : BY_GUIDE_CODE.get(guideCode);
    }

    /** 세금 일정인지 (세금 상세 목록과 같은 기준: kind 에 TAX 가 들어 있음) */
    public static boolean isTax(String kind) {
        return kind != null && kind.toUpperCase(Locale.ROOT).contains("TAX");
    }

    /**
     * @param guideCode 일정에 연결된 항목 코드 (없으면 null)
     * @return 그 공통 일정이 이 종류의 세금인지
     */
    public static boolean matches(CalendarSchedule schedule, String guideCode, String kind) {
        return kind != null && (kind.equals(schedule.getKind()) || kind.equals(forGuideCode(guideCode)));
    }
}
