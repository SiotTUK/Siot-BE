package com.siot.backend.schedule.holiday;

import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 지방세 기한 계산. 지방세기본법 제24조(기한의 특례): 납부 기한이 토요일·일요일·공휴일·대체공휴일·노동절이면
 * 그다음 날이 기한이다 (다음 날도 쉬는 날이면 계속 미룬다).
 * <p>
 * 공휴일은 {@link HolidaySource}(천문연 특일 정보)에서 받는다. 받지 못하면 양력 고정 공휴일과 주말만으로 계산하고
 * {@link Deadline#officialHolidays()} = false 로 알린다 (설날·추석·대체·임시공휴일은 빠지므로 관리자가 확인해야 한다).
 */
@Slf4j
public class TaxDeadlineCalendar {

    /** 양력 고정 공휴일 + 노동절 (제24조가 따로 적은 날) */
    static final Set<MonthDay> FIXED_HOLIDAYS = Set.of(
            MonthDay.of(1, 1), MonthDay.of(3, 1), MonthDay.of(5, 1), MonthDay.of(5, 5), MonthDay.of(6, 6),
            MonthDay.of(8, 15), MonthDay.of(10, 3), MonthDay.of(10, 9), MonthDay.of(12, 25));

    /**
     * @param date             미룬 뒤의 기한
     * @param officialHolidays 공식 공휴일 자료로 계산했는지 (false = 고정 공휴일·주말만)
     */
    public record Deadline(LocalDate date, boolean officialHolidays) {
    }

    private record YearHolidays(Set<LocalDate> dates, boolean official) {
    }

    /** 받지 못한 해를 다시 묻기까지 (한 번 생성에서 회차마다 다시 부르지 않도록) */
    static final Duration RETRY_AFTER = Duration.ofHours(1);

    private final HolidaySource source;
    private final Clock clock;
    /** 공식 자료는 계속 기억한다 */
    private final Map<Integer, YearHolidays> cache = new ConcurrentHashMap<>();
    /** 받지 못한 해 → 다시 물어볼 수 있는 시각 */
    private final Map<Integer, Instant> unavailableUntil = new ConcurrentHashMap<>();

    public TaxDeadlineCalendar(HolidaySource source) {
        this(source, Clock.systemUTC());
    }

    TaxDeadlineCalendar(HolidaySource source, Clock clock) {
        this.source = source;
        this.clock = clock;
    }

    /** 기한이 쉬는 날이면 다음 영업일 */
    public Deadline deadline(LocalDate due) {
        LocalDate date = due;
        boolean official = true;
        while (true) {
            YearHolidays holidays = holidays(date.getYear());
            official &= holidays.official();
            if (!isWeekend(date) && !holidays.dates().contains(date) && !FIXED_HOLIDAYS.contains(MonthDay.from(date))) {
                return new Deadline(date, official);
            }
            date = date.plusDays(1);
        }
    }

    private YearHolidays holidays(int year) {
        YearHolidays cached = cache.get(year);
        if (cached != null) {
            return cached;
        }
        Instant retryAt = unavailableUntil.get(year);
        if (retryAt != null && clock.instant().isBefore(retryAt)) {
            return new YearHolidays(Set.of(), false);
        }
        try {
            YearHolidays official = new YearHolidays(Set.copyOf(source.holidays(year)), true);
            cache.put(year, official);
            unavailableUntil.remove(year);
            return official;
        } catch (HolidaySource.HolidayUnavailableException e) {
            log.warn("Holiday data for {} unavailable, using weekends and fixed holidays only: {}", year, e.getMessage());
            unavailableUntil.put(year, clock.instant().plus(RETRY_AFTER));
            return new YearHolidays(Set.of(), false);
        }
    }

    private static boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }
}
