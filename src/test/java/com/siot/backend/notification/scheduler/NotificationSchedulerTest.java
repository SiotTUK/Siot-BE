package com.siot.backend.notification.scheduler;

import com.siot.backend.checklist.domain.ChecklistProgress;
import com.siot.backend.checklist.domain.Guide;
import com.siot.backend.checklist.domain.ItemType;
import com.siot.backend.checklist.domain.ProgressStatus;
import com.siot.backend.checklist.repository.ChecklistProgressRepository;
import com.siot.backend.checklist.repository.GuideRepository;
import com.siot.backend.checklist.repository.GuideTranslationRepository;
import com.siot.backend.global.config.ClockConfig;
import com.siot.backend.member.domain.AppUser;
import com.siot.backend.member.domain.UserType;
import com.siot.backend.member.repository.AppUserRepository;
import com.siot.backend.notification.domain.Notification;
import com.siot.backend.notification.domain.NotificationPreference;
import com.siot.backend.notification.repository.NotificationPreferenceRepository;
import com.siot.backend.notification.repository.NotificationRepository;
import com.siot.backend.notification.service.NotificationDispatcher;
import com.siot.backend.schedule.domain.CalendarSchedule;
import com.siot.backend.schedule.repository.CalendarScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationSchedulerTest {

    /** 2027-02-28 09:00 KST. 2027 은 평년이라 6/28·6/29·6/30 만료의 신청 시작일이 모두 2/28 이 된다 */
    private static final LocalDate TODAY = LocalDate.of(2027, 2, 28);
    private static final Long USER_ID = 1L;
    private static final Long CL01_GUIDE_ID = 100L;

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationPreferenceRepository preferenceRepository;
    @Mock
    private CalendarScheduleRepository calendarScheduleRepository;
    @Mock
    private GuideTranslationRepository guideTranslationRepository;
    @Mock
    private NotificationDispatcher notificationDispatcher;
    @Mock
    private GuideRepository guideRepository;
    @Mock
    private ChecklistProgressRepository checklistProgressRepository;

    private NotificationScheduler notificationScheduler;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(ZonedDateTime.of(TODAY.atTime(9, 0), ClockConfig.KST).toInstant(), ClockConfig.KST);
        notificationScheduler = new NotificationScheduler(appUserRepository, notificationRepository, preferenceRepository,
                calendarScheduleRepository, guideTranslationRepository, notificationDispatcher,
                guideRepository, checklistProgressRepository, clock);
        lenient().when(notificationRepository.existsByUserIdAndSourceKey(anyLong(), anyString())).thenReturn(false);
        lenient().when(preferenceRepository.findByKindAndUserId(anyString(), anyLong())).thenReturn(Optional.empty());
    }

    private static AppUser user() {
        AppUser user = AppUser.create("user@siot.kr", "hash");
        ReflectionTestUtils.setField(user, "id", USER_ID);
        user.markEmailVerified(OffsetDateTime.now());
        return user;
    }

    private Notification savedNotification() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("체류기간 만료 D-30 알림 생성")
    void stayExpiry_d30() {
        AppUser user = user();
        LocalDate targetExpiry = TODAY.plusDays(30);
        ReflectionTestUtils.setField(user, "stayExpiry", targetExpiry);
        given(appUserRepository.findImpendingStayUsers(any(LocalDate.class), any(LocalDate.class)))
                .willAnswer(inv -> targetExpiry.equals(inv.getArgument(0)) ? List.of(user) : Collections.emptyList());

        notificationScheduler.generateStayExpiryNotifications();

        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    @DisplayName("연장 신청 시작일(만료일 − 4개월)인 회원에게 신청 시작 알림")
    void stayExtensionStart_onApplyStartDate() {
        AppUser user = user();
        ReflectionTestUtils.setField(user, "stayExpiry", LocalDate.of(2027, 6, 28));
        given(appUserRepository.findImpendingStayUsers(LocalDate.of(2027, 6, 28), LocalDate.of(2027, 7, 1)))
                .willReturn(List.of(user));

        notificationScheduler.generateStayExtensionStartNotifications();

        Notification saved = savedNotification();
        assertThat(saved.getKind()).isEqualTo("STAY_EXPIRY");
        assertThat(saved.getTitle()).contains("연장 신청 시작");
    }

    @Test
    @DisplayName("그달에 같은 날짜가 없으면 말일이 신청 시작일 (6/30 만료 → 2/28)")
    void stayExtensionStart_monthEndClamp() {
        AppUser user = user();
        ReflectionTestUtils.setField(user, "stayExpiry", LocalDate.of(2027, 6, 30));
        given(appUserRepository.findImpendingStayUsers(any(LocalDate.class), any(LocalDate.class))).willReturn(List.of(user));

        notificationScheduler.generateStayExtensionStartNotifications();

        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    @DisplayName("조회 범위에 걸렸지만 신청 시작일이 오늘이 아니면 보내지 않는다 (7/1 만료 → 3/1)")
    void stayExtensionStart_notToday() {
        AppUser user = user();
        ReflectionTestUtils.setField(user, "stayExpiry", LocalDate.of(2027, 7, 1));
        given(appUserRepository.findImpendingStayUsers(any(LocalDate.class), any(LocalDate.class))).willReturn(List.of(user));

        notificationScheduler.generateStayExtensionStartNotifications();

        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("외국인등록 알림은 D-7, D-1 에만 조회한다")
    void registration_leadDays() {
        given(guideRepository.findByCode("CL-01")).willReturn(Optional.empty());
        given(appUserRepository.findByEntryDateBetween(any(LocalDate.class), any(LocalDate.class))).willReturn(List.of());

        notificationScheduler.generateRegistrationNotifications();

        verify(appUserRepository, times(2)).findByEntryDateBetween(any(), any());
        verify(appUserRepository).findByEntryDateBetween(TODAY.minusDays(83), TODAY.minusDays(83)); // D-7
        verify(appUserRepository).findByEntryDateBetween(TODAY.minusDays(89), TODAY.minusDays(89)); // D-1
    }

    @Test
    @DisplayName("외국인등록을 아직 안 한 신규 입국자에게 D-7 알림")
    void registration_notDone_sends() {
        AppUser user = registrationUser();
        given(checklistProgressRepository.findByUserIdAndGuideIdAndPeriodKey(USER_ID, CL01_GUIDE_ID, ChecklistProgress.ONCE))
                .willReturn(Optional.empty());

        notificationScheduler.generateRegistrationNotifications();

        assertThat(savedNotification().getKind()).isEqualTo("REGISTRATION");
        verify(preferenceRepository).findByKindAndUserId("CHECKLIST_DEADLINE", USER_ID);
        verify(preferenceRepository, never()).findByKindAndUserId(eq("STAY_EXPIRY"), anyLong());
    }

    @Test
    @DisplayName("체크리스트에서 외국인등록을 완료했으면 알림을 보내지 않는다")
    void registration_done_skips() {
        registrationUser();
        given(checklistProgressRepository.findByUserIdAndGuideIdAndPeriodKey(USER_ID, CL01_GUIDE_ID, ChecklistProgress.ONCE))
                .willReturn(Optional.of(ChecklistProgress.of(USER_ID, CL01_GUIDE_ID, ChecklistProgress.ONCE, ProgressStatus.DONE)));

        notificationScheduler.generateRegistrationNotifications();

        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("체크리스트 마감 알림을 끄면 외국인등록 알림을 보내지 않는다 (체류 만료 설정과 별개)")
    void registration_preferenceOff_skips() {
        registrationUser();
        given(preferenceRepository.findByKindAndUserId("CHECKLIST_DEADLINE", USER_ID))
                .willReturn(Optional.of(NotificationPreference.of("CHECKLIST_DEADLINE", USER_ID, false, 7)));

        notificationScheduler.generateRegistrationNotifications();

        verify(notificationRepository, never()).save(any());
    }

    /** 주민세 개인분 일정 (자동차·주택 조건 없음) */
    private static CalendarSchedule residentTax(LocalDate startsOn, LocalDate endsOn) {
        CalendarSchedule schedule = CalendarSchedule.create(null, "TAX_RESIDENT", startsOn, endsOn,
                "주민세 개인분", "https://www.wetax.go.kr", true, null);
        ReflectionTestUtils.setField(schedule, "id", 10L);
        return schedule;
    }

    private void noTaxSchedulesExcept(Runnable stubs) {
        lenient().when(calendarScheduleRepository.findImpendingSchedules(any(), any())).thenReturn(List.of());
        lenient().when(calendarScheduleRepository.findByEndsOnAndActiveTrue(any())).thenReturn(List.of());
        stubs.run();
    }

    @Test
    @DisplayName("세금 납부 시작일 당일에 알림")
    void tax_onStartDate() {
        CalendarSchedule schedule = residentTax(TODAY, TODAY.plusDays(15));
        noTaxSchedulesExcept(() -> given(calendarScheduleRepository.findImpendingSchedules(TODAY, TODAY)).willReturn(List.of(schedule)));
        given(appUserRepository.findAll()).willReturn(List.of(user()));

        notificationScheduler.generateTaxNotifications();

        Notification saved = savedNotification();
        assertThat(saved.getKind()).isEqualTo("TAX");
        assertThat(saved.getTitle()).contains("오늘부터 납부 기간");
        assertThat(saved.getSourceKey()).isEqualTo("tax:start:10:1");
    }

    @Test
    @DisplayName("세금 납부 마감 D-7, D-1 에 알림 (시작일 D-n 이 아니라 마감일 기준)")
    void tax_beforeDueDate() {
        CalendarSchedule dueIn7 = residentTax(TODAY.minusDays(8), TODAY.plusDays(7));
        noTaxSchedulesExcept(() -> given(calendarScheduleRepository.findByEndsOnAndActiveTrue(TODAY.plusDays(7))).willReturn(List.of(dueIn7)));
        given(appUserRepository.findAll()).willReturn(List.of(user()));

        notificationScheduler.generateTaxNotifications();

        Notification saved = savedNotification();
        assertThat(saved.getTitle()).contains("납부 마감 D-7");
        assertThat(saved.getSourceKey()).isEqualTo("tax:due:7:10:1");
        verify(calendarScheduleRepository).findByEndsOnAndActiveTrue(TODAY.plusDays(1));
        verify(calendarScheduleRepository, never()).findImpendingSchedules(TODAY.plusDays(7), TODAY.plusDays(7));
    }

    @Test
    @DisplayName("자동차세는 자동차가 있는 회원에게만 보낸다")
    void tax_carOnlyForCarOwners() {
        CalendarSchedule carTax = CalendarSchedule.create(null, "TAX_CAR", TODAY, TODAY.plusDays(14), "자동차세", null, true, null);
        ReflectionTestUtils.setField(carTax, "id", 11L);
        noTaxSchedulesExcept(() -> given(calendarScheduleRepository.findImpendingSchedules(TODAY, TODAY)).willReturn(List.of(carTax)));
        given(appUserRepository.findAll()).willReturn(List.of(user())); // 자동차 없음

        notificationScheduler.generateTaxNotifications();

        verify(notificationRepository, never()).save(any());
    }

    /** 입국 83일째(마감 D-7) 신규 입국자 */
    private AppUser registrationUser() {
        AppUser user = user();
        LocalDate entryDate = TODAY.minusDays(83);
        ReflectionTestUtils.setField(user, "entryDate", entryDate);
        ReflectionTestUtils.setField(user, "userType", UserType.NEW);

        Guide cl01 = Guide.create("CL-01", "CHECKLIST", ItemType.REQUIRED, "IMMIGRATION", 1, null, false);
        ReflectionTestUtils.setField(cl01, "id", CL01_GUIDE_ID);
        given(guideRepository.findByCode("CL-01")).willReturn(Optional.of(cl01));
        given(appUserRepository.findByEntryDateBetween(any(LocalDate.class), any(LocalDate.class)))
                .willAnswer(inv -> entryDate.equals(inv.getArgument(0)) ? List.of(user) : List.of());
        return user;
    }
}
