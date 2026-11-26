package com.siot.backend.notification.service;

import com.siot.backend.event.domain.Event;
import com.siot.backend.event.domain.EventSubscription;
import com.siot.backend.event.repository.EventSubscriptionRepository;
import com.siot.backend.event.repository.EventTranslationRepository;
import com.siot.backend.member.domain.AppUser;
import com.siot.backend.member.repository.AppUserRepository;
import com.siot.backend.notification.repository.NotificationPreferenceRepository;
import com.siot.backend.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** #15 즉시·매시 경로가 같은 변경 알림을 동시에 넣어 유니크 제약에 걸리면 중복으로 보고 건너뛴다 */
class EventChangeNotificationWriterTest {

    private final EventSubscriptionRepository subscriptionRepository = mock(EventSubscriptionRepository.class);
    private final EventTranslationRepository translationRepository = mock(EventTranslationRepository.class);
    private final AppUserRepository userRepository = mock(AppUserRepository.class);
    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final NotificationPreferenceRepository preferenceRepository = mock(NotificationPreferenceRepository.class);
    private final EventChangeNotificationWriter writer = new EventChangeNotificationWriter(subscriptionRepository,
            translationRepository, userRepository, notificationRepository, preferenceRepository, Clock.systemUTC(),
            TransactionOperations.withoutTransaction());

    @Test
    void duplicateKeyFromOtherPathIsSkipped() {
        Event event = Event.manual(1L);
        ReflectionTestUtils.setField(event, "id", 9L);
        event.editStartsOn(LocalDate.of(2099, 6, 11), false);
        EventSubscription subscription = EventSubscription.of(3L, 9L, true);
        subscription.recordSchedule("2099-06-10|||");
        AppUser user = AppUser.create("u@siot.kr", "hash");
        ReflectionTestUtils.setField(user, "id", 3L);
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        when(preferenceRepository.findByKindAndUserId(anyString(), anyLong())).thenReturn(Optional.empty());
        when(translationRepository.findByEventIdAndLanguage(anyLong(), anyString())).thenReturn(Optional.empty());
        when(notificationRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk_notification_user_source"));

        assertThat(writer.apply(event, subscription, null)).isFalse();
    }
}
