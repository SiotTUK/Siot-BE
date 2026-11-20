package com.siot.backend.schedule.service;

import com.siot.backend.checklist.domain.GuideTranslation;
import com.siot.backend.checklist.repository.GuideTranslationRepository;
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
        String targetLang = (language != null && !language.isBlank()) ? language : "ko";

        List<ScheduleResponse> result = new ArrayList<>();

        // 1. 공통 생활/세금 일정 조회
        List<CalendarSchedule> schedules = calendarScheduleRepository
                .findByStartsOnBetweenAndActiveTrueOrderByStartsOnAsc(start, end);

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

        // 2. 로그인 회원의 개인 일정 (체류기간 만료 등) 병합 (CAL-04)
        if (userId != null) {
            appUserRepository.findById(userId).ifPresent(user -> {
                LocalDate expiry = user.getStayExpiry();
                if (expiry != null && !expiry.isBefore(start) && !expiry.isAfter(end)) {
                    result.add(new ScheduleResponse(
                            null,
                            null,
                            "STAY_EXPIRY",
                            "체류기간 만료일",
                            expiry,
                            expiry,
                            "체류기간 만료 4개월 전부터 연장 신청이 가능합니다.",
                            "https://www.hikorea.go.kr",
                            true
                    ));
                }

                // 자동차세 (6월, 12월)
                if (user.getResidences().contains(Residence.CAR)) {
                    if (month == 6) {
                        result.add(new ScheduleResponse(
                                null, null, "TAX_CAR", "자동차세 1기분 납부",
                                LocalDate.of(year, 6, 16), LocalDate.of(year, 6, 30),
                                "자차 보유자 대상 자동차세 납부 기간입니다.", "https://www.wetax.go.kr", true
                        ));
                    } else if (month == 12) {
                        result.add(new ScheduleResponse(
                                null, null, "TAX_CAR", "자동차세 2기분 납부",
                                LocalDate.of(year, 12, 16), LocalDate.of(year, 12, 31),
                                "자차 보유자 대상 자동차세 납부 기간입니다.", "https://www.wetax.go.kr", true
                        ));
                    }
                }

                // 재산세 (7월, 9월)
                if (user.getResidences().contains(Residence.HOUSE)) {
                    if (month == 7) {
                        result.add(new ScheduleResponse(
                                null, null, "TAX_PROPERTY", "재산세 1기분 납부",
                                LocalDate.of(year, 7, 16), LocalDate.of(year, 7, 31),
                                "주택 소유자 대상 재산세 납부 기간입니다.", "https://www.wetax.go.kr", true
                        ));
                    } else if (month == 9) {
                        result.add(new ScheduleResponse(
                                null, null, "TAX_PROPERTY", "재산세 2기분 납부",
                                LocalDate.of(year, 9, 16), LocalDate.of(year, 9, 30),
                                "주택 소유자 대상 재산세 납부 기간입니다.", "https://www.wetax.go.kr", true
                        ));
                    }
                }
            });
        }

        result.sort(Comparator.comparing(ScheduleResponse::startsOn));
        return result;
    }

    public List<ScheduleResponse> getYearlySchedules(int year, String language) {
        return getMonthlySchedules(null, year, 1, language);
    }
}
