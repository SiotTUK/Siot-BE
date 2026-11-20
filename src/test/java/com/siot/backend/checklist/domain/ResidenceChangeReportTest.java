package com.siot.backend.checklist.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ResidenceChangeReportTest {

    private static final LocalDate MOVE = LocalDate.of(2027, 3, 1);

    @Test
    @DisplayName("체류지 변경 신고는 이사일 + 15일, F-4 거소 이전 신고는 + 14일 (CHK-02, CHK-10)")
    void deadlines() {
        assertThat(ResidenceChangeReport.deadline("LC-01-1", MOVE)).isEqualTo(LocalDate.of(2027, 3, 16));
        assertThat(ResidenceChangeReport.deadline("LC-01-2", MOVE)).isEqualTo(LocalDate.of(2027, 3, 15));
    }

    @Test
    @DisplayName("F-4 회원은 거소 이전 신고, 그 외는 체류지 변경 신고")
    void reportCodeFor() {
        assertThat(ResidenceChangeReport.reportCodeFor(true)).isEqualTo("LC-01-2");
        assertThat(ResidenceChangeReport.reportCodeFor(false)).isEqualTo("LC-01-1");
    }

    @Test
    @DisplayName("알림 대상 이사일 범위는 두 기한을 모두 덮는다")
    void moveDateRange() {
        LocalDate deadline = LocalDate.of(2027, 3, 16);
        assertThat(ResidenceChangeReport.earliestMoveDateFor(deadline)).isEqualTo(LocalDate.of(2027, 3, 1));
        assertThat(ResidenceChangeReport.latestMoveDateFor(deadline)).isEqualTo(LocalDate.of(2027, 3, 2));
    }
}
