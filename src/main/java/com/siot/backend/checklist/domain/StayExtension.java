package com.siot.backend.checklist.domain;

import java.time.LocalDate;

/**
 * 체류기간 연장 일정 규칙 (CHK-10, CAL-04, NTF-01).
 * 체크리스트(CL-05), 생활 달력, 알림 스케줄러가 같은 규칙을 쓰도록 한곳에 둔다.
 */
public final class StayExtension {

    /** 연장 신청 시작일 = 체류 만료일 − 4개월 */
    public static final int APPLY_MONTHS_BEFORE_EXPIRY = 4;

    /** 만료 30일 전부터 강조 표시 */
    public static final int HIGHLIGHT_DAYS_BEFORE_EXPIRY = 30;

    private StayExtension() {
    }

    /**
     * 연장 신청 시작일. 그달에 같은 날짜가 없으면 그달 말일이다 (예: 6/30 만료 → 2/28).
     * {@link LocalDate#minusMonths} 가 이렇게 동작한다.
     */
    public static LocalDate applyStartDate(LocalDate expiry) {
        return expiry == null ? null : expiry.minusMonths(APPLY_MONTHS_BEFORE_EXPIRY);
    }

    /** 만료 30일 전부터(만료일이 지난 뒤 포함) 강조한다 */
    public static boolean isHighlighted(LocalDate expiry, LocalDate today) {
        return expiry != null && !today.isBefore(expiry.minusDays(HIGHLIGHT_DAYS_BEFORE_EXPIRY));
    }
}
