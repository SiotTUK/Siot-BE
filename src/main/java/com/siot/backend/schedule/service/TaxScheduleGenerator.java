package com.siot.backend.schedule.service;

import com.siot.backend.checklist.domain.Guide;
import com.siot.backend.checklist.repository.GuideRepository;
import com.siot.backend.schedule.domain.CalendarSchedule;
import com.siot.backend.schedule.domain.TaxKind;
import com.siot.backend.schedule.holiday.TaxDeadlineCalendar;
import com.siot.backend.schedule.repository.CalendarScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 공통 세금 일정 자동 생성 (CAL-03, NTF-01 세금 알림의 기준).
 * 시흥시 지방세 정기분 납부 기간은 매년 같다 (생활수칙 tax-car·tax-property·tax-resident 와 같은 출처):
 * 자동차세 6·12월, 재산세 7·9월, 주민세 개인분 8월, 모두 16일부터 그달 말일까지.
 * 말일이 쉬는 날이면 지방세기본법 제24조에 따라 다음 영업일까지 ({@link TaxDeadlineCalendar}).
 * <p>
 * 이미 그달에 같은 세금 일정이 있으면 (꺼 둔 것, 관리자가 고친 것 포함) 만들지도 고치지도 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaxScheduleGenerator {

    /** 정기분 회차: 세금 종류, 생활수칙 항목 코드, 달 */
    record Period(String kind, String guideCode, int month) {
    }

    static final List<Period> PERIODS = List.of(
            new Period(TaxKind.CAR, "tax-car", 6),
            new Period(TaxKind.PROPERTY, "tax-property", 7),
            new Period(TaxKind.RESIDENT, "tax-resident", 8),
            new Period(TaxKind.PROPERTY, "tax-property", 9),
            new Period(TaxKind.CAR, "tax-car", 12));

    /** 납부 시작일 (매 회차 16일) */
    static final int START_DAY = 16;

    /**
     * @param created          새로 만든 일정
     * @param officialHolidays 마감일을 공식 공휴일 자료로 계산했는지. false 면 설날·추석·대체·임시공휴일을 빼고
     *                         계산했으므로 관리자가 마감일을 확인해야 한다 (만든 일정이 없으면 true)
     */
    public record Result(int year, List<CalendarSchedule> created, boolean officialHolidays) {
    }

    private final CalendarScheduleRepository scheduleRepository;
    private final GuideRepository guideRepository;
    private final TaxDeadlineCalendar deadlineCalendar;

    @Transactional
    public Result generate(int year) {
        List<CalendarSchedule> existing = scheduleRepository.findByStartsOnBetweenOrderByStartsOnAscIdAsc(
                LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
        Map<Long, String> existingGuideCodes = guideRepository.findAllById(existing.stream()
                        .map(CalendarSchedule::getGuideId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Guide::getId, Guide::getCode));
        Map<String, Guide> taxGuides = guideRepository.findByCodeIn(
                        PERIODS.stream().map(Period::guideCode).distinct().toList())
                .stream().collect(Collectors.toMap(Guide::getCode, g -> g));

        List<CalendarSchedule> created = new ArrayList<>();
        boolean official = true;
        for (Period period : PERIODS) {
            YearMonth month = YearMonth.of(year, period.month());
            boolean exists = existing.stream().anyMatch(cs -> YearMonth.from(cs.getStartsOn()).equals(month)
                    && TaxKind.matches(cs, cs.getGuideId() == null ? null : existingGuideCodes.get(cs.getGuideId()),
                    period.kind()));
            if (exists) {
                continue;
            }
            TaxDeadlineCalendar.Deadline deadline = deadlineCalendar.deadline(month.atEndOfMonth());
            official &= deadline.officialHolidays();
            Guide guide = taxGuides.get(period.guideCode());
            created.add(CalendarSchedule.create(
                    guide == null || guide.isDeleted() ? null : guide.getId(),
                    period.kind(),
                    month.atDay(START_DAY),
                    deadline.date(),
                    null,
                    guide == null ? null : guide.getSourceUrl(),
                    true,
                    null));
        }
        if (!created.isEmpty()) {
            scheduleRepository.saveAll(created);
            log.info("Tax schedules for {}: created {} (official holidays: {})", year, created.size(), official);
        }
        return new Result(year, created, official);
    }
}
