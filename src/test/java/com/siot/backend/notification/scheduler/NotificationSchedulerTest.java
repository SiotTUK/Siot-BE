package com.siot.backend.notification.scheduler;

import com.siot.backend.checklist.repository.GuideTranslationRepository;
import com.siot.backend.member.domain.AppUser;
import com.siot.backend.member.domain.Residence;
import com.siot.backend.member.domain.UserType;
import com.siot.backend.member.repository.AppUserRepository;
import com.siot.backend.notification.domain.Notification;
import com.siot.backend.notification.repository.NotificationPreferenceRepository;
import com.siot.backend.notification.repository.NotificationRepository;
import com.siot.backend.notification.service.NotificationDispatcher;
import com.siot.backend.schedule.domain.CalendarSchedule;
import com.siot.backend.schedule.repository.CalendarScheduleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationSchedulerTest {

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

    @InjectMocks
    private NotificationScheduler notificationScheduler;

    @Test
    @DisplayName("체류기간 만료 D-30 알림 생성 스케줄러 검증")
    void generateStayExpiryNotifications_creates_notification() {
        // given
        Long userId = 1L;
        LocalDate targetExpiry = LocalDate.now().plusDays(30);

        AppUser user = AppUser.create("user@siot.kr", "hash");
        ReflectionTestUtils.setField(user, "id", userId);
        user.markEmailVerified(OffsetDateTime.now());
        ReflectionTestUtils.setField(user, "stayExpiry", targetExpiry);

        given(appUserRepository.findImpendingStayUsers(any(LocalDate.class), any(LocalDate.class)))
                .willAnswer(inv -> {
                    LocalDate d = inv.getArgument(0);
                    if (d.equals(targetExpiry)) {
                        return List.of(user);
                    }
                    return Collections.emptyList();
                });

        given(preferenceRepository.findByKindAndUserId(eq("STAY_EXPIRY"), eq(userId)))
                .willReturn(Optional.empty()); // 기본 활성화
        given(notificationRepository.existsByUserIdAndSourceKey(eq(userId), anyString()))
                .willReturn(false);

        // when
        notificationScheduler.generateStayExpiryNotifications();

        // then
        verify(notificationRepository).save(any(Notification.class));
    }
}
