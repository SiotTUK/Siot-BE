package com.siot.backend.schedule.service;

import com.siot.backend.checklist.domain.Guide;
import com.siot.backend.checklist.repository.GuideRepository;
import com.siot.backend.global.exception.BusinessException;
import com.siot.backend.global.exception.ErrorCode;
import com.siot.backend.schedule.domain.CalendarSchedule;
import com.siot.backend.schedule.domain.TaxKind;
import com.siot.backend.schedule.dto.AdminScheduleRequest;
import com.siot.backend.schedule.dto.AdminScheduleResponse;
import com.siot.backend.schedule.dto.TaxScheduleGenerateResponse;
import com.siot.backend.schedule.repository.CalendarScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** 관리자: 달력 공통 일정 조회·등록·수정, 세금 일정 자동 생성 실행 */
@Service
@RequiredArgsConstructor
public class AdminScheduleService {

    private final CalendarScheduleRepository scheduleRepository;
    private final GuideRepository guideRepository;
    private final TaxScheduleGenerator taxScheduleGenerator;

    /** 그해 시작하는 일정 (꺼 둔 일정 포함), 시작일 순. kind 를 주면 그 종류만 (연결 항목으로 같은 세금인 일정 포함) */
    @Transactional(readOnly = true)
    public List<AdminScheduleResponse> list(int year, String kind) {
        checkYear(year);
        List<CalendarSchedule> schedules = scheduleRepository.findByStartsOnBetweenOrderByStartsOnAscIdAsc(
                LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
        Map<Long, String> codes = guideCodes(schedules);
        return schedules.stream()
                .filter(cs -> kind == null || kind.isBlank()
                        || TaxKind.matches(cs, codeOf(codes, cs), kind.strip()))
                .map(cs -> AdminScheduleResponse.of(cs, codeOf(codes, cs)))
                .toList();
    }

    @Transactional
    public AdminScheduleResponse create(Long adminId, AdminScheduleRequest request) {
        Guide guide = guide(request.guideCode());
        checkDates(request);
        CalendarSchedule schedule = CalendarSchedule.create(guide == null ? null : guide.getId(), request.kind().strip(),
                request.startsOn(), request.endsOn(), blankToNull(request.targetDescription()),
                blankToNull(request.sourceUrl()), request.active() == null || request.active(), adminId);
        scheduleRepository.saveAndFlush(schedule);
        return AdminScheduleResponse.of(schedule, guide == null ? null : guide.getCode());
    }

    @Transactional
    public AdminScheduleResponse update(Long adminId, Long scheduleId, AdminScheduleRequest request) {
        CalendarSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND));
        Guide guide = guide(request.guideCode());
        checkDates(request);
        schedule.edit(guide == null ? null : guide.getId(), request.kind().strip(), request.startsOn(), request.endsOn(),
                blankToNull(request.targetDescription()), blankToNull(request.sourceUrl()),
                request.active() == null || request.active(), adminId);
        scheduleRepository.flush();
        return AdminScheduleResponse.of(schedule, guide == null ? null : guide.getCode());
    }

    /** 그해 정기분 세금 일정 중 없는 회차를 만든다 (이미 있는 일정은 그대로) */
    @Transactional
    public TaxScheduleGenerateResponse generateTaxSchedules(int year) {
        checkYear(year);
        TaxScheduleGenerator.Result result = taxScheduleGenerator.generate(year);
        Map<Long, String> codes = guideCodes(result.created());
        return new TaxScheduleGenerateResponse(year,
                result.created().stream().map(cs -> AdminScheduleResponse.of(cs, codeOf(codes, cs))).toList(),
                result.officialHolidays());
    }

    private Guide guide(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return guideRepository.findByCode(code.strip())
                .filter(g -> !g.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.GUIDE_NOT_FOUND));
    }

    private static void checkDates(AdminScheduleRequest request) {
        if (request.endsOn() != null && request.endsOn().isBefore(request.startsOn())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "endsOn 은 startsOn 보다 빠를 수 없습니다.");
        }
    }

    private static void checkYear(int year) {
        if (year < CalendarScheduleService.MIN_YEAR || year > CalendarScheduleService.MAX_YEAR) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "year 는 " + CalendarScheduleService.MIN_YEAR + "~" + CalendarScheduleService.MAX_YEAR + " 입니다.");
        }
    }

    private Map<Long, String> guideCodes(Collection<CalendarSchedule> schedules) {
        List<Long> ids = schedules.stream().map(CalendarSchedule::getGuideId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return guideRepository.findAllById(ids).stream().collect(Collectors.toMap(Guide::getId, Guide::getCode));
    }

    private static String codeOf(Map<Long, String> codes, CalendarSchedule cs) {
        return cs.getGuideId() == null ? null : codes.get(cs.getGuideId());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
