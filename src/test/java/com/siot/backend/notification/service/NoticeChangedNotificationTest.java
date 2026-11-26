package com.siot.backend.notification.service;

import com.siot.backend.checklist.domain.ChecklistProgress;
import com.siot.backend.checklist.domain.Guide;
import com.siot.backend.checklist.domain.GuideTranslation;
import com.siot.backend.checklist.domain.ItemType;
import com.siot.backend.checklist.domain.MemberLifeChange;
import com.siot.backend.checklist.domain.ProgressStatus;
import com.siot.backend.checklist.repository.ChecklistProgressRepository;
import com.siot.backend.checklist.repository.GuideTranslationRepository;
import com.siot.backend.checklist.repository.MemberLifeChangeRepository;
import com.siot.backend.global.config.ClockConfig;
import com.siot.backend.member.domain.AppUser;
import com.siot.backend.member.repository.AppUserRepository;
import com.siot.backend.notification.domain.Notification;
import com.siot.backend.notification.repository.NotificationPreferenceRepository;
import com.siot.backend.notification.repository.NotificationRepository;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** NTF-07 안내 내용 변경 알림 대상: 기준 날짜를 입력했고 그 항목을 완료하지 않은 회원 */
@ExtendWith(MockitoExtension.class)
class NoticeChangedNotificationTest {

    private static final OffsetDateTime NOW =
            ZonedDateTime.of(2027, 3, 2, 10, 0, 0, 0, ClockConfig.KST).toOffsetDateTime();

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationPreferenceRepository preferenceRepository;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private GuideTranslationRepository guideTranslationRepository;
    @Mock
    private ChecklistProgressRepository checklistProgressRepository;
    @Mock
    private MemberLifeChangeRepository memberLifeChangeRepository;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository, preferenceRepository, appUserRepository,
                guideTranslationRepository, checklistProgressRepository, memberLifeChangeRepository,
                Clock.fixed(NOW.toInstant(), ClockConfig.KST));
        lenient().when(notificationRepository.existsByUserIdAndSourceKey(anyLong(), anyString())).thenReturn(false);
        lenient().when(preferenceRepository.findByKindAndUserId(anyString(), anyLong())).thenReturn(Optional.empty());
        lenient().when(checklistProgressRepository.findByUserIdAndGuideIdAndPeriodKey(anyLong(), anyLong(), anyString()))
                .thenReturn(Optional.empty());
    }

    private static AppUser user(long id, String language) {
        AppUser user = AppUser.create("user" + id + "@siot.kr", "hash");
        ReflectionTestUtils.setField(user, "id", id);
        user.changeLanguage(language);
        return user;
    }

    private static Guide changedGuide(String code, String kind, String lifeChangeCode) {
        Guide guide = Guide.create(code, kind, ItemType.REQUIRED, null, 1, lifeChangeCode, false);
        ReflectionTestUtils.setField(guide, "id", 10L);
        guide.recordEdit(1L, true, NOW);
        return guide;
    }

    @Test
    @DisplayName("체크리스트 항목: 입국일을 입력했고 완료하지 않은 회원에게만, 회원 언어로 보낸다")
    void checklist_targetsEntryDateMembersNotDone() {
        Guide guide = changedGuide("CL-01", "CHECKLIST", null);
        AppUser open = user(1L, "en");
        AppUser done = user(2L, "ko");
        given(appUserRepository.findByEntryDateIsNotNull()).willReturn(List.of(open, done));
        given(checklistProgressRepository.findByUserIdAndGuideIdAndPeriodKey(2L, 10L, ChecklistProgress.ONCE))
                .willReturn(Optional.of(ChecklistProgress.of(2L, 10L, ChecklistProgress.ONCE, ProgressStatus.DONE)));
        given(guideTranslationRepository.findByGuideIdAndLanguage(10L, "en"))
                .willReturn(Optional.of(GuideTranslation.create(10L, "en")));

        notificationService.createNoticeChangedNotifications(guide);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getKind()).isEqualTo("NOTICE_CHANGED");
        assertThat(saved.getGuideId()).isEqualTo(10L);
        assertThat(saved.getTitle()).startsWith("[Information updated]");
        verify(appUserRepository, never()).findAll();
    }

    @Test
    @DisplayName("생활 변화 할 일: 그 생활 변화를 등록한 회원에게, 그 회차를 완료하지 않았으면 보낸다")
    void lifeChangeTask_targetsMembersWithThatLifeChange() {
        Guide guide = changedGuide("LC-01-1", "LIFE_CHANGE_TASK", "LC-01");
        LocalDate moveDate = LocalDate.of(2027, 2, 20);
        given(memberLifeChangeRepository.findByLifeChangeCode("LC-01"))
                .willReturn(List.of(MemberLifeChange.of(3L, "LC-01", moveDate)));
        given(appUserRepository.findById(3L)).willReturn(Optional.of(user(3L, "ko")));

        notificationService.createNoticeChangedNotifications(guide);

        verify(checklistProgressRepository).findByUserIdAndGuideIdAndPeriodKey(3L, 10L, moveDate.toString());
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    @DisplayName("생활수칙은 기준 날짜가 없으므로 알림을 보내지 않는다")
    void rule_sendsNothing() {
        Guide guide = changedGuide("RUL-01", "RULE", null);

        notificationService.createNoticeChangedNotifications(guide);

        verifyNoInteractions(appUserRepository, notificationRepository);
    }
}
