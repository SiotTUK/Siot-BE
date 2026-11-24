package com.siot.backend.schedule.service;

import com.siot.backend.checklist.domain.GuideTranslation;
import com.siot.backend.checklist.domain.StayExtension;
import com.siot.backend.global.config.ClockConfig;
import com.siot.backend.checklist.repository.GuideTranslationRepository;
import com.siot.backend.global.common.SupportedLanguages;
import com.siot.backend.member.domain.AppUser;
import com.siot.backend.member.domain.Residence;
import com.siot.backend.member.repository.AppUserRepository;
import com.siot.backend.schedule.domain.CalendarSchedule;
import com.siot.backend.schedule.dto.ScheduleResponse;
import com.siot.backend.schedule.repository.CalendarScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 생활 달력 월간/연간 일정 및 개인 세금·체류 일정 병합 서비스 (CAL-01 ~ CAL-04)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarScheduleService {

    private final CalendarScheduleRepository calendarScheduleRepository;
    private final GuideTranslationRepository guideTranslationRepository;
    private final AppUserRepository appUserRepository;

    public List<ScheduleResponse> getMonthlySchedules(Long userId, int year, int month, String language) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.with(TemporalAdjusters.lastDayOfMonth());
        String targetLang = SupportedLanguages.orDefault(language);

        // 1. 공통 생활/세금 일정 조회
        List<ScheduleResponse> result = commonSchedules(calendarScheduleRepository.findActiveOverlapping(start, end), targetLang);

        // 2. 로그인 회원의 개인 일정 (체류기간 만료 등) 병합 (CAL-04)
        if (userId != null) {
            appUserRepository.findById(userId).ifPresent(user ->
                    addPersonalSchedules(result, user, start, end, CalendarScheduleMessages.forLanguage(targetLang)));
        }

        result.sort(Comparator.comparing(ScheduleResponse::startsOn));
        return result;
    }

    /**
     * CAL-01 연간 일정: 해당 연도 1~12월의 공통 세금·생활 일정 (시작일 기준).
     * 회원 정보를 묻지 않으므로 개인 일정은 넣지 않고, 납부 대상은 각 일정의 대상 설명으로 보여 준다.
     */
    public List<ScheduleResponse> getYearlySchedules(int year, String language) {
        String targetLang = (language != null && !language.isBlank()) ? language : "ko";
        List<ScheduleResponse> result = commonSchedules(
                calendarScheduleRepository.findByStartsOnBetweenAndActiveTrueOrderByStartsOnAsc(
                        LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)),
                targetLang);
        result.sort(Comparator.comparing(ScheduleResponse::startsOn));
        return result;
    }

    private List<ScheduleResponse> commonSchedules(List<CalendarSchedule> schedules, String targetLang) {
        List<ScheduleResponse> result = new ArrayList<>();

        for (CalendarSchedule cs : schedules) {
            String title = cs.getKind();
            if (cs.getGuideId() != null) {
                GuideTranslation tr = guideTranslationRepository
                        .findByGuideIdAndLanguage(cs.getGuideId(), targetLang)
                        .orElse(null);
                if (tr != null) title = tr.getTitle();
            }

            result.add(new ScheduleResponse(
                    cs.getId(),
                    cs.getGuideId(),
                    cs.getKind(),
                    title,
                    cs.getStartsOn(),
                    cs.getEndsOn(),
                    cs.getTargetDescription(),
                    cs.getSourceUrl(),
                    false
            ));
        }
        return result;
    }

    /** 로그인 회원의 개인 일정: 체류기간 연장·만료 (CAL-04), 자동차세·재산세 */
    private void addPersonalSchedules(List<ScheduleResponse> result, AppUser user, LocalDate start, LocalDate end,
                                      CalendarScheduleMessages messages) {
        LocalDate expiry = user.getStayExpiry();
        if (expiry != null) {
            boolean highlighted = StayExtension.isHighlighted(expiry, LocalDate.now(ClockConfig.KST));
            LocalDate applyStart = StayExtension.applyStartDate(expiry);
            if (!applyStart.isBefore(start) && !applyStart.isAfter(end)) {
                result.add(new ScheduleResponse(
                        null,
                        null,
                        "STAY_EXTENSION_START",
                        messages.extensionTitle(),
                        applyStart,
                        applyStart,
                        messages.extensionWindow().formatted(applyStart, expiry),
                        "https://www.hikorea.go.kr",
                        true,
                        highlighted
                ));
            }
            if (!expiry.isBefore(start) && !expiry.isAfter(end)) {
                result.add(new ScheduleResponse(
                        null,
                        null,
                        "STAY_EXPIRY",
                        messages.expiryTitle(),
                        expiry,
                        expiry,
                        messages.expiryDescription().formatted(applyStart),
                        "https://www.hikorea.go.kr",
                        true,
                        highlighted
                ));
            }
        }

        int month = start.getMonthValue();
        if ((month == 6 || month == 12) && user.getResidences().contains(Residence.CAR)) {
            result.add(new ScheduleResponse(
                    null, null, "TAX_CAR", messages.carTaxTitle().formatted(month == 6 ? 1 : 2),
                    start.withDayOfMonth(16), end, messages.carTaxDescription(), "https://www.wetax.go.kr", true
            ));
        }

        if ((month == 7 || month == 9) && user.getResidences().contains(Residence.HOUSE)) {
            result.add(new ScheduleResponse(
                    null, null, "TAX_PROPERTY", messages.propertyTaxTitle().formatted(month == 7 ? 1 : 2),
                    start.withDayOfMonth(16), end, messages.propertyTaxDescription(), "https://www.wetax.go.kr", true
            ));
        }
    }
}
