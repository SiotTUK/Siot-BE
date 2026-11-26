package com.siot.backend.checklist.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class StayExtensionTest {

    @ParameterizedTest(name = "{0} 만료 → {1} 신청 시작")
    @CsvSource({
            "2027-11-15, 2027-07-15",
            "2027-06-30, 2027-02-28", // 2월에 30일이 없으면 말일
            "2028-06-30, 2028-02-29", // 윤년
            "2027-07-31, 2027-03-31",
            "2028-01-31, 2027-09-30"  // 연도가 바뀌고, 9월에 31일이 없으면 말일
    })
    @DisplayName("연장 신청 시작일 = 만료일 − 4개월, 그달에 없는 날짜면 말일 (CHK-10)")
    void applyStartDate(LocalDate expiry, LocalDate expected) {
        assertThat(StayExtension.applyStartDate(expiry)).isEqualTo(expected);
    }

    @Test
    @DisplayName("만료일을 입력하지 않았으면 신청 시작일도 강조도 없다")
    void noExpiry() {
        assertThat(StayExtension.applyStartDate(null)).isNull();
        assertThat(StayExtension.isHighlighted(null, LocalDate.of(2027, 1, 1))).isFalse();
    }

    @ParameterizedTest(name = "만료 {0}일 전 → 강조 {1}")
    @CsvSource({"31, false", "30, true", "1, true", "0, true", "-5, true"})
    @DisplayName("만료 30일 전부터 강조, 만료 후에도 강조 (CAL-04)")
    void highlight(int daysBeforeExpiry, boolean expected) {
        LocalDate expiry = LocalDate.of(2027, 6, 30);
        assertThat(StayExtension.isHighlighted(expiry, expiry.minusDays(daysBeforeExpiry))).isEqualTo(expected);
    }
}
