package com.siot.backend.schedule.service;

import com.siot.backend.checklist.domain.GuideTranslation;
import com.siot.backend.checklist.repository.GuideRepository;
import com.siot.backend.checklist.repository.GuideTranslationRepository;
import com.siot.backend.member.domain.AppUser;
import com.siot.backend.member.domain.Purpose;
import com.siot.backend.member.domain.Residence;
import com.siot.backend.member.domain.UserType;
import com.siot.backend.member.repository.AppUserRepository;
import com.siot.backend.rag.repository.GuideDocumentReferenceRepository;
import com.siot.backend.schedule.domain.CalendarSchedule;
import com.siot.backend.schedule.dto.ScheduleResponse;
import com.siot.backend.schedule.dto.TaxDetailResponse;
import com.siot.backend.schedule.repository.CalendarScheduleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CalendarScheduleServiceTest {

    @Mock
    private CalendarScheduleRepository calendarScheduleRepository;
    @Mock
    private GuideTranslationRepository guideTranslationRepository;
    @Mock
    private GuideRepository guideRepository;
    @Mock
    private GuideDocumentReferenceRepository guideDocumentReferenceRepository;
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
        given(calendarScheduleRepository.findActiveOverlapping(any(), any()))
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

    @Test
    @DisplayName("CAL-03 세금 상세 조회 시 번역 및 출처 페이지 정보 반환 검증")
    void getTaxDetail_returns_tax_detail_with_source_pages() {
        // given
        Long scheduleId = 100L;
        Long guideId = 10L;

        CalendarSchedule schedule = CalendarSchedule.create(
                guideId, "TAX_PROPERTY", LocalDate.of(2026, 7, 16), LocalDate.of(2026, 7, 31),
                "재산세(주택1기분, 건축물) 납부", "https://wetax.go.kr", true, 1L
        );
        org.springframework.test.util.ReflectionTestUtils.setField(schedule, "id", scheduleId);
        given(calendarScheduleRepository.findById(scheduleId)).willReturn(Optional.of(schedule));

        GuideTranslation translation = GuideTranslation.create(
                guideId, "ko", "재산세 납부 안내", "재산세 납부 요약", "재산세 상세 본문", "7/16~7/31",
                "시청 세정과 또는 위택스", List.of("신분증", "고지서"), "해당없음", "지방세법", "가산세 부과", "031-310-2000"
        );
        given(guideTranslationRepository.findByGuideIdAndLanguage(guideId, "ko"))
                .willReturn(Optional.of(translation));

        com.siot.backend.rag.domain.GuideDocumentReference docRef =
                org.mockito.Mockito.mock(com.siot.backend.rag.domain.GuideDocumentReference.class);
        given(docRef.getPageStart()).willReturn(42);
        given(docRef.getPageEnd()).willReturn(43);
        given(guideDocumentReferenceRepository.findFirstByGuideIdOrderByCreatedAtDesc(guideId))
                .willReturn(Optional.of(docRef));

        // when
        TaxDetailResponse response = calendarScheduleService.getTaxDetail(scheduleId, "ko");

        // then
        assertThat(response).isNotNull();
        assertThat(response.scheduleId()).isEqualTo(scheduleId);
        assertThat(response.title()).isEqualTo("재산세 납부 안내");
        assertThat(response.summary()).isEqualTo("재산세 납부 요약");
        assertThat(response.content()).isEqualTo("재산세 상세 본문");
        assertThat(response.sourcePages()).isEqualTo("p.42~43");
    }

    @Test
    @DisplayName("연장 신청 시작일(만료일 − 4개월)이 든 달에 STAY_EXTENSION_START 일정을 보여 준다 (CAL-04)")
    void getMonthlySchedules_stayExtensionStart() {
        // given: 2099-10-31 만료 → 신청 시작 2099-06-30 (먼 미래라 강조 아님)
        Long userId = 1L;
        AppUser user = AppUser.create("test@siot.kr", "hash");
        user.updateOnboarding(UserType.LIVING, LocalDate.of(2025, 1, 1), Purpose.WORK, Set.of(), LocalDate.of(2099, 10, 31));
        given(appUserRepository.findById(userId)).willReturn(Optional.of(user));
        given(calendarScheduleRepository.findActiveOverlapping(any(), any())).willReturn(List.of());

        // when
        List<ScheduleResponse> june = calendarScheduleService.getMonthlySchedules(userId, 2099, 6, "ko");
        List<ScheduleResponse> october = calendarScheduleService.getMonthlySchedules(userId, 2099, 10, "ko");

        // then
        assertThat(june).singleElement().satisfies(r -> {
            assertThat(r.kind()).isEqualTo("STAY_EXTENSION_START");
            assertThat(r.startsOn()).isEqualTo(LocalDate.of(2099, 6, 30));
            assertThat(r.targetDescription()).isEqualTo(
                    "2099-06-30부터 체류 만료일(2099-10-31)까지 체류기간 연장을 신청할 수 있습니다.");
            assertThat(r.personal()).isTrue();
            assertThat(r.highlighted()).isFalse();
        });
        assertThat(october).singleElement().satisfies(r -> {
            assertThat(r.kind()).isEqualTo("STAY_EXPIRY");
            assertThat(r.targetDescription()).contains("2099-06-30");
        });
    }

    @Test
    @DisplayName("연간 일정은 1월만이 아니라 1~12월 공통 일정을 시작일 순으로 돌려준다 (CAL-01)")
    void getYearlySchedules_wholeYear() {
        CalendarSchedule car2 = CalendarSchedule.create(null, "TAX_CAR", LocalDate.of(2027, 12, 16), LocalDate.of(2027, 12, 31),
                "자동차 보유자", null, true, null);
        CalendarSchedule resident = CalendarSchedule.create(null, "TAX_RESIDENT", LocalDate.of(2027, 8, 16), LocalDate.of(2027, 8, 31),
                "모든 주민", null, true, null);
        CalendarSchedule car1 = CalendarSchedule.create(null, "TAX_CAR", LocalDate.of(2027, 6, 16), LocalDate.of(2027, 6, 30),
                "자동차 보유자", null, true, null);
        given(calendarScheduleRepository.findByStartsOnBetweenAndActiveTrueOrderByStartsOnAsc(
                LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31)))
                .willReturn(List.of(car2, resident, car1));

        List<ScheduleResponse> responses = calendarScheduleService.getYearlySchedules(null, 2027, "ko");

        assertThat(responses).extracting(ScheduleResponse::startsOn).containsExactly(
                LocalDate.of(2027, 6, 16), LocalDate.of(2027, 8, 16), LocalDate.of(2027, 12, 16));
        assertThat(responses).allSatisfy(r -> assertThat(r.personal()).isFalse());
        verifyNoInteractions(appUserRepository);
    }

    @Test
    @DisplayName("연간 일정에 로그인 회원의 개인 일정(체류·자동차세·재산세)을 월간과 같은 규칙으로 넣는다 (BE #20)")
    void getYearlySchedules_includesPersonalSchedules() {
        AppUser user = AppUser.create("yearly@siot.kr", "hash");
        user.updateOnboarding(UserType.LIVING, LocalDate.of(2025, 1, 1), Purpose.WORK,
                Set.of(Residence.CAR, Residence.HOUSE), LocalDate.of(2027, 10, 20));
        given(appUserRepository.findById(1L)).willReturn(Optional.of(user));
        given(calendarScheduleRepository.findByStartsOnBetweenAndActiveTrueOrderByStartsOnAsc(
                LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31))).willReturn(List.of());

        List<ScheduleResponse> responses = calendarScheduleService.getYearlySchedules(1L, 2027, "en");

        assertThat(responses).allSatisfy(r -> assertThat(r.personal()).isTrue());
        assertThat(responses).extracting(ScheduleResponse::kind).containsExactly(
                "TAX_CAR",              // 6월 16일
                "STAY_EXTENSION_START", // 6월 20일 (만료 4개월 전)
                "TAX_PROPERTY",         // 7월
                "TAX_PROPERTY",         // 9월
                "STAY_EXPIRY",          // 10월
                "TAX_CAR");             // 12월
        assertThat(responses).filteredOn(r -> r.kind().equals("STAY_EXPIRY"))
                .singleElement().satisfies(r -> assertThat(r.startsOn()).isEqualTo(LocalDate.of(2027, 10, 20)));
        assertThat(responses).allSatisfy(r -> assertThat(r.dDay()).isNotNull());
    }

    @Test
    @DisplayName("만료 30일 안이면 체류 만료 일정을 강조한다 (CAL-04)")
    void getMonthlySchedules_stayExpiryHighlighted() {
        Long userId = 1L;
        LocalDate expiry = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(10);
        AppUser user = AppUser.create("test@siot.kr", "hash");
        user.updateOnboarding(UserType.LIVING, LocalDate.of(2025, 1, 1), Purpose.WORK, Set.of(), expiry);
        given(appUserRepository.findById(userId)).willReturn(Optional.of(user));
        given(calendarScheduleRepository.findActiveOverlapping(any(), any())).willReturn(List.of());

        List<ScheduleResponse> responses = calendarScheduleService.getMonthlySchedules(
                userId, expiry.getYear(), expiry.getMonthValue(), "ko");

        assertThat(responses).filteredOn(r -> "STAY_EXPIRY".equals(r.kind()))
                .singleElement().satisfies(r -> assertThat(r.highlighted()).isTrue());
    }

    @ParameterizedTest
    @CsvSource({
            "en, First day to apply for a stay extension, Stay expiry date, Vehicle tax payment (installment 1), Property tax payment (installment 2)",
            "zh, 居留期限延长申请开始日, 居留期满日, 汽车税缴纳（第1期）, 财产税缴纳（第2期）",
            "vi, Ngày bắt đầu nộp đơn gia hạn thời gian lưu trú, Ngày hết hạn lưu trú, Nộp thuế ô tô (đợt 1), Nộp thuế tài sản (đợt 2)"
    })
    void monthlyPersonalSchedulesUseRequestedLanguage(
            String language, String extensionTitle, String expiryTitle, String carTaxTitle, String propertyTaxTitle) {
        AppUser user = AppUser.create("test@siot.kr", "hash");
        user.updateOnboarding(UserType.LIVING, null, Purpose.WORK, Set.of(Residence.CAR, Residence.HOUSE),
                LocalDate.of(2099, 10, 31));
        given(appUserRepository.findById(1L)).willReturn(Optional.of(user));

        List<ScheduleResponse> june = calendarScheduleService.getMonthlySchedules(1L, 2099, 6, language);
        List<ScheduleResponse> september = calendarScheduleService.getMonthlySchedules(1L, 2099, 9, language);
        List<ScheduleResponse> october = calendarScheduleService.getMonthlySchedules(1L, 2099, 10, language);

        assertThat(june).extracting(ScheduleResponse::title).containsExactly(carTaxTitle, extensionTitle);
        assertThat(june.getFirst().targetDescription()).isNotBlank().doesNotContain("자동차세");
        assertThat(june.getLast().targetDescription()).contains("2099-06-30", "2099-10-31").doesNotContain("오늘");
        assertThat(september).singleElement().satisfies(r -> {
            assertThat(r.title()).isEqualTo(propertyTaxTitle);
            assertThat(r.targetDescription()).isNotBlank().doesNotContain("재산세");
        });
        assertThat(october).singleElement().satisfies(r -> {
            assertThat(r.title()).isEqualTo(expiryTitle);
            assertThat(r.targetDescription()).contains("2099-06-30").doesNotContain("체류기간");
        });
    }

    @Test
    @DisplayName("이번 달 할일은 마감이 지나지 않은 이번 달 일정을 마감 임박 순으로 남은 일수와 준다 (CAL-02)")
    void monthlyTodos_thisMonth() {
        LocalDate today = LocalDate.of(2027, 6, 20);
        CalendarSchedule past = CalendarSchedule.create(null, "TAX_RESIDENT", LocalDate.of(2027, 6, 1), LocalDate.of(2027, 6, 10),
                "모든 주민", null, true, null);
        CalendarSchedule car = CalendarSchedule.create(null, "TAX_CAR", LocalDate.of(2027, 6, 16), LocalDate.of(2027, 6, 30),
                "자동차 보유자", null, true, null);
        CalendarSchedule today_ = CalendarSchedule.create(null, "TAX_RESIDENT", LocalDate.of(2027, 6, 20), LocalDate.of(2027, 6, 20),
                "모든 주민", null, true, null);
        given(calendarScheduleRepository.findActiveOverlapping(LocalDate.of(2027, 6, 1), LocalDate.of(2027, 6, 30)))
                .willReturn(List.of(past, car, today_));

        com.siot.backend.schedule.dto.MonthlyTodoResponse result = calendarScheduleService.getMonthlyTodos(null, "ko", today);

        assertThat(result.nextMonth()).isFalse();
        assertThat(result.month()).isEqualTo(6);
        assertThat(result.items()).extracting(com.siot.backend.schedule.dto.MonthlyTodoResponse.Item::dDay)
                .containsExactly(0L, 10L);
    }

    @Test
    @DisplayName("이번 달에 남은 일정이 없으면 다음 달 일정을 nextMonth=true 로 준다 (CAL-02)")
    void monthlyTodos_fallsBackToNextMonth() {
        LocalDate today = LocalDate.of(2027, 7, 20);
        CalendarSchedule property = CalendarSchedule.create(null, "TAX_PROPERTY", LocalDate.of(2027, 7, 16), LocalDate.of(2027, 7, 19),
                "주택 소유자", null, true, null);
        CalendarSchedule resident = CalendarSchedule.create(null, "TAX_RESIDENT", LocalDate.of(2027, 8, 16), LocalDate.of(2027, 8, 31),
                "모든 주민", null, true, null);
        given(calendarScheduleRepository.findActiveOverlapping(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 31)))
                .willReturn(List.of(property));
        given(calendarScheduleRepository.findActiveOverlapping(LocalDate.of(2027, 8, 1), LocalDate.of(2027, 8, 31)))
                .willReturn(List.of(resident));

        com.siot.backend.schedule.dto.MonthlyTodoResponse result = calendarScheduleService.getMonthlyTodos(null, "ko", today);

        assertThat(result.nextMonth()).isTrue();
        assertThat(result.year()).isEqualTo(2027);
        assertThat(result.month()).isEqualTo(8);
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.schedule().kind()).isEqualTo("TAX_RESIDENT");
            assertThat(item.dDay()).isEqualTo(42L);
        });
    }

    @Test
    @DisplayName("공통 일정 제목은 번역이 없으면 한국어 제목 (내부 코드 TAX_CAR 를 보여 주지 않는다, COM-02)")
    void yearlySchedules_fallBackToKoreanTitle() {
        CalendarSchedule car = CalendarSchedule.create(10L, "TAX_CAR", LocalDate.of(2027, 6, 16), LocalDate.of(2027, 6, 30),
                "자동차 보유자", null, true, null);
        given(calendarScheduleRepository.findByStartsOnBetweenAndActiveTrueOrderByStartsOnAsc(any(), any()))
                .willReturn(List.of(car));
        given(guideTranslationRepository.findByGuideIdAndLanguage(10L, "vi")).willReturn(Optional.empty());
        given(guideTranslationRepository.findByGuideIdAndLanguage(10L, "ko")).willReturn(Optional.of(
                GuideTranslation.create(10L, "ko", "자동차세 납부", null, "본문", null, null, Collections.emptyList(),
                        null, null, null, null)));

        assertThat(calendarScheduleService.getYearlySchedules(null, 2027, "vi"))
                .singleElement().satisfies(r -> assertThat(r.title()).isEqualTo("자동차세 납부"));
        // 지원하지 않는 언어는 한국어로
        assertThat(calendarScheduleService.getYearlySchedules(null, 2027, "ja"))
                .singleElement().satisfies(r -> assertThat(r.title()).isEqualTo("자동차세 납부"));
    }
}
