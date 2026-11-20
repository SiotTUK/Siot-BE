package com.siot.backend.schedule.service;

import com.siot.backend.checklist.domain.GuideTranslation;
import com.siot.backend.checklist.repository.GuideTranslationRepository;
import com.siot.backend.member.domain.AppUser;
import com.siot.backend.member.domain.Purpose;
import com.siot.backend.member.domain.Residence;
import com.siot.backend.member.domain.UserType;
import com.siot.backend.member.repository.AppUserRepository;
import com.siot.backend.schedule.domain.CalendarSchedule;
import com.siot.backend.schedule.dto.ScheduleResponse;
import com.siot.backend.schedule.repository.CalendarScheduleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CalendarScheduleServiceTest {

    @Mock
    private CalendarScheduleRepository calendarScheduleRepository;
    @Mock
    private GuideTranslationRepository guideTranslationRepository;
    @Mock
    private AppUserRepository appUserRepository;

    @InjectMocks
    private CalendarScheduleService calendarScheduleService;

    @Test
    @DisplayName("월간 일정 조회 시 공통 세금 일정 및 회원 개인 일정(자동차세/체류만료) 병합 검증")
    void getMonthlySchedules_merges_personal_events() {
        // given
        Long userId = 1L;
        int year = 2026;
        int month = 6;

        AppUser user = AppUser.create("test@siot.kr", "hash");
        user.updateOnboarding(UserType.LIVING, LocalDate.of(2025, 1, 1), Purpose.WORK,
                Set.of(Residence.CAR), LocalDate.of(2026, 6, 25));

        given(appUserRepository.findById(userId)).willReturn(Optional.of(user));

        CalendarSchedule commonSchedule = CalendarSchedule.create(
                10L, "TAX_CAR", LocalDate.of(2026, 6, 16), LocalDate.of(2026, 6, 30),
                "자동차세 1기분 납부", "https://wetax.go.kr", true, 1L
        );
        given(calendarScheduleRepository.findByStartsOnBetweenAndActiveTrueOrderByStartsOnAsc(any(), any()))
                .willReturn(List.of(commonSchedule));

        GuideTranslation translation = GuideTranslation.create(
                10L, "ko", "자동차세 납부", "요약", "본문", "기한", "장소", Collections.emptyList(), "수수료", "법령", "과태료", "문의"
        );
        given(guideTranslationRepository.findByGuideIdAndLanguage(10L, "ko"))
                .willReturn(Optional.of(translation));

        // when
        List<ScheduleResponse> responses = calendarScheduleService.getMonthlySchedules(userId, year, month, "ko");

        // then
        assertThat(responses).isNotEmpty();
        // 개인 일정 (체류기간 만료일 6/25) 포함 여부
        boolean hasStayExpiry = responses.stream().anyMatch(r -> "STAY_EXPIRY".equals(r.kind()) && r.personal());
        assertThat(hasStayExpiry).isTrue();

        // 자동차세 일정 (6/16 ~ 6/30) 포함 여부
        boolean hasCarTax = responses.stream().anyMatch(r -> "TAX_CAR".equals(r.kind()));
        assertThat(hasCarTax).isTrue();
    }
}
