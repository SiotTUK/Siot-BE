package com.siot.backend.notification.scheduler;

import com.siot.backend.checklist.domain.ChecklistProgress;
import com.siot.backend.checklist.domain.Guide;
import com.siot.backend.checklist.domain.ItemType;
import com.siot.backend.checklist.domain.ProgressStatus;
import com.siot.backend.checklist.repository.ChecklistProgressRepository;
import com.siot.backend.checklist.repository.GuideRepository;
import com.siot.backend.checklist.repository.GuideTranslationRepository;
import com.siot.backend.checklist.repository.MemberLifeChangeRepository;
import com.siot.backend.checklist.domain.MemberLifeChange;
import com.siot.backend.event.domain.Event;
import com.siot.backend.event.domain.EventSubscription;
import com.siot.backend.event.repository.EventRepository;
import com.siot.backend.event.repository.EventSubscriptionRepository;
import com.siot.backend.event.repository.EventTranslationRepository;
import com.siot.backend.member.domain.Purpose;
import com.siot.backend.member.domain.Residence;
import java.util.Set;
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
    private EventRepository eventRepository;
    @Mock
    private EventSubscriptionRepository eventSubscriptionRepository;
    @Mock
    private EventTranslationRepository eventTranslationRepository;
    @Mock
    private NotificationDispatcher notificationDispatcher;
    @Mock
    private GuideRepository guideRepository;
    @Mock
    private ChecklistProgressRepository checklistProgressRepository;
    @Mock
    private MemberLifeChangeRepository memberLifeChangeRepository;

    private NotificationScheduler notificationScheduler;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(ZonedDateTime.of(TODAY.atTime(9, 0), ClockConfig.KST).toInstant(), ClockConfig.KST);
        notificationScheduler = new NotificationScheduler(appUserRepository, notificationRepository, preferenceRepository,
                calendarScheduleRepository, guideTranslationRepository,
                eventRepository, eventSubscriptionRepository, eventTranslationRepository, notificationDispatcher,
                guideRepository, checklistProgressRepository, memberLifeChangeRepository, clock);
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

        Notification saved = savedNotification();
        assertThat(saved.getKind()).isEqualTo("REGISTRATION");
        assertThat(saved.getGuideId()).isEqualTo(CL01_GUIDE_ID); // 누르면 외국인등록 상세로 (NTF-04)
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

    // ---- NTF-08 체류지 변경 신고 ----

    private static final Long LC0101_GUIDE_ID = 201L;
    private static final Long LC0102_GUIDE_ID = 202L;

    /** 이사 등록 회원. moveDate 를 이사일로, f4 면 F-4 거소신고자 */
    private AppUser mover(LocalDate moveDate, boolean f4) {
        AppUser user = user();
        user.updateOnboarding(UserType.LIVING, TODAY.minusYears(1), Purpose.WORK,
                f4 ? Set.of(Residence.F4) : Set.of(), null);
        given(memberLifeChangeRepository.findByLifeChangeCodeAndBaseDateBetween(eq("LC-01"), any(), any()))
                .willAnswer(inv -> {
                    LocalDate from = inv.getArgument(1);
                    LocalDate to = inv.getArgument(2);
                    return !moveDate.isBefore(from) && !moveDate.isAfter(to)
                            ? List.of(MemberLifeChange.of(USER_ID, "LC-01", moveDate)) : List.of();
                });
        lenient().when(appUserRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        Guide lc0101 = Guide.create("LC-01-1", "LIFE_CHANGE_TASK", ItemType.REQUIRED, "IMMIGRATION", 1, "LC-01", false);
        ReflectionTestUtils.setField(lc0101, "id", LC0101_GUIDE_ID);
        Guide lc0102 = Guide.create("LC-01-2", "LIFE_CHANGE_TASK", ItemType.REQUIRED, "IMMIGRATION", 2, "LC-01", false);
        ReflectionTestUtils.setField(lc0102, "id", LC0102_GUIDE_ID);
        lenient().when(guideRepository.findByCode("LC-01-1")).thenReturn(Optional.of(lc0101));
        lenient().when(guideRepository.findByCode("LC-01-2")).thenReturn(Optional.of(lc0102));
        return user;
    }

    @Test
    @DisplayName("체류지 변경 신고: 이사일 + 15일 마감의 D-7 에 알림, 항목으로 연결")
    void residenceReport_d7() {
        LocalDate moveDate = TODAY.minusDays(8); // 마감 = 이사일 + 15 = 오늘 + 7
        mover(moveDate, false);
        given(checklistProgressRepository.findByUserIdAndGuideIdAndPeriodKey(USER_ID, LC0101_GUIDE_ID, moveDate.toString()))
                .willReturn(Optional.empty());

        notificationScheduler.generateResidenceReportNotifications();

        Notification saved = savedNotification();
        assertThat(saved.getKind()).isEqualTo("RESIDENCE_REPORT");
        assertThat(saved.getTitle()).isEqualTo("체류지 변경 신고 마감 D-7 안내");
        assertThat(saved.getGuideId()).isEqualTo(LC0101_GUIDE_ID);
        verify(preferenceRepository).findByKindAndUserId("CHECKLIST_DEADLINE", USER_ID);
    }

    @Test
    @DisplayName("F-4 거소 이전 신고는 이사일 + 14일 마감으로 D-1 알림")
    void residenceReport_overseasKorean_d1() {
        LocalDate moveDate = TODAY.minusDays(13); // 마감 = 이사일 + 14 = 오늘 + 1
        mover(moveDate, true);
        given(checklistProgressRepository.findByUserIdAndGuideIdAndPeriodKey(USER_ID, LC0102_GUIDE_ID, moveDate.toString()))
                .willReturn(Optional.empty());

        notificationScheduler.generateResidenceReportNotifications();

        Notification saved = savedNotification();
        assertThat(saved.getTitle()).isEqualTo("거소 이전 신고 마감 D-1 안내");
        assertThat(saved.getGuideId()).isEqualTo(LC0102_GUIDE_ID);
    }

    @Test
    @DisplayName("기한이 다른 사람(일반 15일)의 이사일이 범위에 걸려도 14일 기준으로는 보내지 않는다")
    void residenceReport_wrongDeadlineSkipped() {
        // 이사일 + 14 = 오늘 + 1 이지만 일반 회원이라 마감은 오늘 + 2 → D-1·D-7 어디에도 해당 없음
        mover(TODAY.minusDays(13), false);

        notificationScheduler.generateResidenceReportNotifications();

        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("체크리스트에서 그 이사 회차의 신고를 완료했으면 보내지 않는다")
    void residenceReport_done_skips() {
        LocalDate moveDate = TODAY.minusDays(8);
        mover(moveDate, false);
        given(checklistProgressRepository.findByUserIdAndGuideIdAndPeriodKey(USER_ID, LC0101_GUIDE_ID, moveDate.toString()))
                .willReturn(Optional.of(ChecklistProgress.of(USER_ID, LC0101_GUIDE_ID, moveDate.toString(), ProgressStatus.DONE)));

        notificationScheduler.generateResidenceReportNotifications();

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

    // ---------------------------------------------------------------- 알림 문구 언어 (COM-02)

    @Test
    @DisplayName("알림 제목·본문은 회원의 화면 언어로 만든다")
    void messagesUseMemberLanguage() {
        AppUser user = user();
        user.changeLanguage("en");
        LocalDate targetExpiry = TODAY.plusDays(30);
        given(appUserRepository.findImpendingStayUsers(any(LocalDate.class), any(LocalDate.class)))
                .willAnswer(inv -> targetExpiry.equals(inv.getArgument(0)) ? List.of(user) : Collections.emptyList());

        notificationScheduler.generateStayExpiryNotifications();

        Notification saved = savedNotification();
        assertThat(saved.getLocale()).isEqualTo("en");
        assertThat(saved.getTitle()).isEqualTo("Stay expiry in 30 day(s)");
        assertThat(saved.getBody()).contains(targetExpiry.toString()).doesNotContain("체류");
    }

    // ---------------------------------------------------------------- 행사 알림 (NTF-02)

    private static final Long EVENT_ID = 500L;

    private NotificationScheduler schedulerAt(java.time.LocalDateTime now) {
        Clock clock = Clock.fixed(ZonedDateTime.of(now, ClockConfig.KST).toInstant(), ClockConfig.KST);
        return new NotificationScheduler(appUserRepository, notificationRepository, preferenceRepository,
                calendarScheduleRepository, guideTranslationRepository,
                eventRepository, eventSubscriptionRepository, eventTranslationRepository, notificationDispatcher,
                guideRepository, checklistProgressRepository, memberLifeChangeRepository, clock);
    }

    private Event event(LocalDate startsOn, java.time.LocalTime startsAt) {
        Event event = Event.manual(1L);
        ReflectionTestUtils.setField(event, "id", EVENT_ID);
        event.editStartsOn(startsOn, false);
        event.editStartsAt(startsAt, false);
        return event;
    }

    /** 행사 알림 신청자 1명, 시작·마감·변경 후보는 비어 있게 */
    private AppUser subscribed() {
        AppUser user = user();
        lenient().when(eventSubscriptionRepository.findByEventIdAndEnabledTrue(EVENT_ID))
                .thenReturn(List.of(EventSubscription.of(USER_ID, EVENT_ID, true)));
        lenient().when(appUserRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        lenient().when(eventRepository.findByStartsOnBetweenAndStatus(any(), any(), anyString())).thenReturn(List.of());
        lenient().when(eventRepository.findByStatusAndApplicationDeadlineBetween(anyString(), any(), any())).thenReturn(List.of());
        lenient().when(eventSubscriptionRepository.findEnabledForCurrentEvents(any())).thenReturn(List.of());
        lenient().when(eventTranslationRepository.findByEventIdAndLanguage(anyLong(), anyString())).thenReturn(Optional.empty());
        return user;
    }

    @Test
    @DisplayName("시작 시각을 아는 행사는 시작 24시간 안에 들어오면 시작 알림을 보낸다")
    void eventStart_within24Hours() {
        subscribed();
        Event event = event(TODAY.plusDays(1), java.time.LocalTime.of(14, 0));
        given(eventRepository.findByStartsOnBetweenAndStatus(TODAY, TODAY.plusDays(1), "ACTIVE")).willReturn(List.of(event));

        schedulerAt(TODAY.atTime(14, 0)).generateEventNotifications();

        Notification saved = savedNotification();
        assertThat(saved.getKind()).isEqualTo("EVENT");
        assertThat(saved.getEventId()).isEqualTo(EVENT_ID);
        assertThat(saved.getBody()).contains(TODAY.plusDays(1) + " 14:00");
    }

    @Test
    @DisplayName("시작까지 24시간 넘게 남았으면 아직 보내지 않는다")
    void eventStart_moreThan24Hours() {
        subscribed();
        Event event = event(TODAY.plusDays(1), java.time.LocalTime.of(14, 0));
        given(eventRepository.findByStartsOnBetweenAndStatus(TODAY, TODAY.plusDays(1), "ACTIVE")).willReturn(List.of(event));

        schedulerAt(TODAY.atTime(13, 0)).generateEventNotifications();

        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("시작 시각을 모르는 행사는 전날 오전 9시 이후에 보내고, 자정에는 보내지 않는다")
    void eventStart_dateOnly() {
        subscribed();
        Event event = event(TODAY.plusDays(1), null);
        given(eventRepository.findByStartsOnBetweenAndStatus(TODAY, TODAY.plusDays(1), "ACTIVE")).willReturn(List.of(event));

        schedulerAt(TODAY.atTime(0, 0)).generateEventNotifications();
        verify(notificationRepository, never()).save(any());

        schedulerAt(TODAY.atTime(9, 0)).generateEventNotifications();
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    @DisplayName("신청 마감 24시간 안에 들어온 행사는 신청 마감 D-1 알림을 보낸다")
    void eventApplicationDeadline() {
        subscribed();
        Event event = event(TODAY.plusDays(10), null);
        OffsetDateTime deadline = ZonedDateTime.of(TODAY.plusDays(1).atTime(18, 0), ClockConfig.KST).toOffsetDateTime();
        event.editApplicationDeadline(deadline, false);
        given(eventRepository.findByStatusAndApplicationDeadlineBetween(eq("ACTIVE"), any(), any())).willReturn(List.of(event));

        schedulerAt(TODAY.atTime(20, 0)).generateEventNotifications();

        Notification saved = savedNotification();
        assertThat(saved.getTitle()).contains("신청 마감");
        assertThat(saved.getBody()).contains(TODAY.plusDays(1) + " 18:00");
    }

    @Test
    @DisplayName("신청할 때 본 일정과 지금 일정이 다르면 변경 알림을 보내고 기준 일정을 갱신한다")
    void eventScheduleChanged() {
        subscribed();
        Event event = event(TODAY.plusDays(10), java.time.LocalTime.of(10, 0));
        EventSubscription subscription = EventSubscription.of(USER_ID, EVENT_ID, true);
        subscription.recordSchedule(TODAY.plusDays(7) + "||10:00|");
        given(eventSubscriptionRepository.findEnabledForCurrentEvents(TODAY)).willReturn(List.of(subscription));
        given(eventRepository.findAllById(List.of(EVENT_ID))).willReturn(List.of(event));

        notificationScheduler.generateEventNotifications();

        Notification saved = savedNotification();
        assertThat(saved.getTitle()).contains("일정이 변경");
        assertThat(saved.getBody()).contains(TODAY.plusDays(10) + " 10:00");
        assertThat(subscription.getScheduleSnapshot()).isEqualTo(event.scheduleSnapshot());
        verify(eventSubscriptionRepository).save(subscription);
    }

    @Test
    @DisplayName("기준 일정이 없는 (기능 전) 신청은 알림 없이 지금 일정을 기록한다")
    void eventScheduleSnapshotBackfilled() {
        subscribed();
        Event event = event(TODAY.plusDays(10), null);
        EventSubscription subscription = EventSubscription.of(USER_ID, EVENT_ID, true);
        given(eventSubscriptionRepository.findEnabledForCurrentEvents(TODAY)).willReturn(List.of(subscription));
        given(eventRepository.findAllById(List.of(EVENT_ID))).willReturn(List.of(event));

        notificationScheduler.generateEventNotifications();

        verify(notificationRepository, never()).save(any());
        assertThat(subscription.getScheduleSnapshot()).isEqualTo(event.scheduleSnapshot());
    }
}
