package com.siot.backend.schedule.holiday;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 지방세기본법 제24조: 기한이 토·일·공휴일·대체공휴일·노동절이면 다음 날 (쉬는 날이 이어지면 계속) */
class TaxDeadlineCalendarTest {

    @Test
    void weekdayDeadlineStays() {
        TaxDeadlineCalendar calendar = new TaxDeadlineCalendar(year -> Set.of(LocalDate.of(year, 1, 1)));

        // 2026-06-30 화요일
        assertThat(calendar.deadline(LocalDate.of(2026, 6, 30)))
                .isEqualTo(new TaxDeadlineCalendar.Deadline(LocalDate.of(2026, 6, 30), true));
    }

    @Test
    void weekendMovesToMonday() {
        TaxDeadlineCalendar calendar = new TaxDeadlineCalendar(year -> Set.of(LocalDate.of(year, 1, 1)));

        // 2027-07-31 토요일 → 8-02 월요일
        assertThat(calendar.deadline(LocalDate.of(2027, 7, 31)).date()).isEqualTo(LocalDate.of(2027, 8, 2));
    }

    @Test
    void chuseokAndSubstituteHolidayAfterWeekendKeepMovingTheDeadline() {
        // 2028 추석 연휴 10-02~04 (10-03 개천절과 겹쳐 10-05 대체공휴일)
        TaxDeadlineCalendar calendar = new TaxDeadlineCalendar(year -> Set.of(LocalDate.of(2028, 1, 1),
                LocalDate.of(2028, 10, 2), LocalDate.of(2028, 10, 3), LocalDate.of(2028, 10, 4),
                LocalDate.of(2028, 10, 5)));

        // 2028-09-30 토요일 → 10-01 일 → 추석·대체공휴일 → 10-06 금요일
        assertThat(calendar.deadline(LocalDate.of(2028, 9, 30)))
                .isEqualTo(new TaxDeadlineCalendar.Deadline(LocalDate.of(2028, 10, 6), true));
    }

    @Test
    void deadlineCanMoveIntoTheNextYear() {
        TaxDeadlineCalendar calendar = new TaxDeadlineCalendar(year -> Set.of(LocalDate.of(year, 1, 1)));

        // 2033-12-31 토요일 → 2034-01-01 일요일·신정 → 01-02 월요일
        assertThat(calendar.deadline(LocalDate.of(2033, 12, 31)).date()).isEqualTo(LocalDate.of(2034, 1, 2));
    }

    @Test
    void withoutHolidayDataUsesWeekendsAndFixedHolidaysAndSaysSo() {
        TaxDeadlineCalendar calendar = new TaxDeadlineCalendar(year -> {
            throw new HolidaySource.HolidayUnavailableException("no key");
        });

        // 주말은 그대로 미룬다
        assertThat(calendar.deadline(LocalDate.of(2027, 7, 31)))
                .isEqualTo(new TaxDeadlineCalendar.Deadline(LocalDate.of(2027, 8, 2), false));
        // 추석은 알 수 없다 (10-02 월요일을 영업일로 본다)
        assertThat(calendar.deadline(LocalDate.of(2028, 9, 30)))
                .isEqualTo(new TaxDeadlineCalendar.Deadline(LocalDate.of(2028, 10, 2), false));
        // 양력 고정 공휴일은 안다: 2025-10-03 금요일 개천절 → 10-06 월요일
        assertThat(calendar.deadline(LocalDate.of(2025, 10, 3)).date()).isEqualTo(LocalDate.of(2025, 10, 6));
    }

    @Test
    void retriesUnavailableHolidayDataAfterAWhile() {
        int[] calls = {0};
        java.time.Instant[] now = {java.time.Instant.parse("2027-12-01T00:00:00Z")};
        java.time.Clock clock = new java.time.Clock() {
            @Override
            public java.time.ZoneId getZone() {
                return java.time.ZoneOffset.UTC;
            }

            @Override
            public java.time.Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public java.time.Instant instant() {
                return now[0];
            }
        };
        TaxDeadlineCalendar calendar = new TaxDeadlineCalendar(year -> {
            if (calls[0]++ == 0) {
                throw new HolidaySource.HolidayUnavailableException("timeout");
            }
            return Set.of(LocalDate.of(year, 1, 1), LocalDate.of(2028, 10, 2), LocalDate.of(2028, 10, 3),
                    LocalDate.of(2028, 10, 4), LocalDate.of(2028, 10, 5));
        }, clock);

        assertThat(calendar.deadline(LocalDate.of(2028, 9, 30)).officialHolidays()).isFalse();
        // 곧바로 다시 물어도 부르지 않는다
        assertThat(calendar.deadline(LocalDate.of(2028, 6, 30)).officialHolidays()).isFalse();
        assertThat(calls[0]).isEqualTo(1);

        now[0] = now[0].plus(TaxDeadlineCalendar.RETRY_AFTER);
        assertThat(calendar.deadline(LocalDate.of(2028, 9, 30)))
                .isEqualTo(new TaxDeadlineCalendar.Deadline(LocalDate.of(2028, 10, 6), true));
        assertThat(calls[0]).isEqualTo(2);
    }

    // ---------------------------------------------------------------- 천문연 응답

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void parsesKasiHolidaysAsArrayOrSingleItem() {
        String many = """
                {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},
                 "body":{"items":{"item":[
                   {"dateKind":"01","dateName":"1월1일","isHoliday":"Y","locdate":20280101,"seq":1},
                   {"dateKind":"01","dateName":"추석","isHoliday":"Y","locdate":20281003,"seq":1},
                   {"dateKind":"01","dateName":"대체공휴일","isHoliday":"Y","locdate":"20281005","seq":1},
                   {"dateKind":"02","dateName":"식목일","isHoliday":"N","locdate":20280405,"seq":1}]},
                 "numOfRows":100,"pageNo":1,"totalCount":4}}}""";
        assertThat(KasiHolidaySource.parse(many, jsonMapper)).containsExactlyInAnyOrder(
                LocalDate.of(2028, 1, 1), LocalDate.of(2028, 10, 3), LocalDate.of(2028, 10, 5));

        String one = """
                {"response":{"header":{"resultCode":"00"},"body":{"items":{"item":
                   {"dateName":"1월1일","isHoliday":"Y","locdate":20280101}},"totalCount":1}}}""";
        assertThat(KasiHolidaySource.parse(one, jsonMapper)).containsExactly(LocalDate.of(2028, 1, 1));

        String none = """
                {"response":{"header":{"resultCode":"00"},"body":{"items":"","totalCount":0}}}""";
        assertThat(KasiHolidaySource.parse(none, jsonMapper)).isEmpty();
    }

    @Test
    void kasiErrorsAreUnavailable() {
        // 키 오류는 XML 로 온다
        assertThatThrownBy(() -> KasiHolidaySource.parse(
                "<OpenAPI_ServiceResponse><cmmMsgHeader><returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</returnAuthMsg>"
                        + "</cmmMsgHeader></OpenAPI_ServiceResponse>", jsonMapper))
                .isInstanceOf(HolidaySource.HolidayUnavailableException.class);
        assertThatThrownBy(() -> KasiHolidaySource.parse(
                "{\"response\":{\"header\":{\"resultCode\":\"30\",\"resultMsg\":\"SERVICE KEY IS NOT REGISTERED\"}}}",
                jsonMapper))
                .isInstanceOf(HolidaySource.HolidayUnavailableException.class)
                .hasMessageContaining("30");
    }

    @Test
    void missingKeyIsUnavailableWithoutCalling() {
        HolidayProperties props = new HolidayProperties("http://unused", "", null);
        KasiHolidaySource source = new KasiHolidaySource(null, props, jsonMapper);

        // 키가 없으면 부르지 않는다
        assertThatThrownBy(() -> source.holidays(2030))
                .isInstanceOf(HolidaySource.HolidayUnavailableException.class)
                .hasMessageContaining("api-key");
    }
}
