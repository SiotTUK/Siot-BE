package com.siot.backend.checklist.domain;

import java.time.LocalDate;

/**
 * 이사(생활 변화 LC-01) 후 체류지 변경 신고 규칙 (CHK-02, CHK-10, NTF-08).
 * 체크리스트와 알림 스케줄러가 같은 기한을 쓰도록 한곳에 둔다.
 */
public final class ResidenceChangeReport {

    /** 생활 변화: 이사 */
    public static final String LIFE_CHANGE_CODE = "LC-01";

    /** 체류지 변경 신고 (출입국관리법 제36조): 이사일 + 15일 */
    public static final String RESIDENCE_REPORT_CODE = "LC-01-1";

    /** 거소 이전 신고 (재외동포법 제6조, F-4 거소신고자): 이사일 + 14일 */
    public static final String OVERSEAS_KOREAN_REPORT_CODE = "LC-01-2";

    static final int RESIDENCE_REPORT_DAYS = 15;
    static final int OVERSEAS_KOREAN_REPORT_DAYS = 14;

    private ResidenceChangeReport() {
    }

    public static boolean isReportCode(String guideCode) {
        return RESIDENCE_REPORT_CODE.equals(guideCode) || OVERSEAS_KOREAN_REPORT_CODE.equals(guideCode);
    }

    /** 회원에게 해당하는 신고 항목: F-4 거소신고자는 거소 이전 신고, 그 외는 체류지 변경 신고 */
    public static String reportCodeFor(boolean overseasKorean) {
        return overseasKorean ? OVERSEAS_KOREAN_REPORT_CODE : RESIDENCE_REPORT_CODE;
    }

    /** 신고 마감일 (초일 불산입: 이사일 + n일) */
    public static LocalDate deadline(String guideCode, LocalDate moveDate) {
        int days = OVERSEAS_KOREAN_REPORT_CODE.equals(guideCode) ? OVERSEAS_KOREAN_REPORT_DAYS : RESIDENCE_REPORT_DAYS;
        return moveDate.plusDays(days);
    }

    /** 마감 D-n 알림 대상이 될 수 있는 이사일 범위 (두 기한을 모두 덮는다) */
    public static LocalDate earliestMoveDateFor(LocalDate deadline) {
        return deadline.minusDays(RESIDENCE_REPORT_DAYS);
    }

    public static LocalDate latestMoveDateFor(LocalDate deadline) {
        return deadline.minusDays(OVERSEAS_KOREAN_REPORT_DAYS);
    }
}
