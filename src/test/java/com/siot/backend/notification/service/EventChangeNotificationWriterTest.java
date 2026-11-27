package com.siot.backend.notification.service;

import com.siot.backend.event.collect.EventVisibilityProperties;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** #15 즉시·매시 경로가 같은 변경 알림을 동시에 넣어 유니크 제약에 걸리면 중복으로 보고 건너뛴다 */
class EventChangeNotificationWriterTest {

    private final EventSubscriptionRepository subscriptionRepository = mock(EventSubscriptionRepository.class);
    private final EventTranslationRepository translationRepository = mock(EventTranslationRepository.class);
    private final AppUserRepository userRepository = mock(AppUserRepository.class);
    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final NotificationPreferenceRepository preferenceRepository = mock(NotificationPreferenceRepository.class);
    private final EventChangeNotificationWriter writer = writer("SIHEUNG");

    private EventChangeNotificationWriter writer(String... publicRegions) {
        return new EventChangeNotificationWriter(subscriptionRepository, translationRepository, userRepository,
                notificationRepository, preferenceRepository, Clock.systemUTC(),
                new EventVisibilityProperties(Set.of(publicRegions)), TransactionOperations.withoutTransaction());
    }

    @Test
    void duplicateKeyFromOtherPathIsSkipped() {
        Event event = changedEvent("SIHEUNG");
        EventSubscription subscription = subscriptionBeforeChange();
        stubSubscriber();
        when(notificationRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk_notification_user_source"));

        assertThat(writer.apply(event, subscription, null)).isFalse();
    }

    /** 숨긴 동안에는 기록을 바꾸지 않아, 다시 공개하면 마지막으로 안내한 일정과 달라 알린다 */
    @Test
    void hiddenRegionKeepsRecordedScheduleAndNotifiesOnceVisible() {
        Event event = changedEvent("GYEONGGI");
        EventSubscription subscription = subscriptionBeforeChange();
        stubSubscriber();

        assertThat(writer.apply(event, subscription, null)).isFalse();
        assertThat(subscription.getScheduleSnapshot()).isEqualTo("2099-06-10|||");
        verify(notificationRepository, never()).saveAndFlush(any());
        verify(subscriptionRepository, never()).save(any());

        assertThat(writer("SIHEUNG", "GYEONGGI").apply(event, subscription, null)).isTrue();
        assertThat(subscription.getScheduleSnapshot()).isEqualTo(event.scheduleSnapshot());
        verify(notificationRepository).saveAndFlush(any());
    }

    private static Event changedEvent(String region) {
        Event event = Event.manual(1L);
        ReflectionTestUtils.setField(event, "id", 9L);
        ReflectionTestUtils.setField(event, "regionCode", region);
        event.editStartsOn(LocalDate.of(2099, 6, 11), false);
        return event;
    }

    private static EventSubscription subscriptionBeforeChange() {
        EventSubscription subscription = EventSubscription.of(3L, 9L, true);
        subscription.recordSchedule("2099-06-10|||");
        return subscription;
    }

    private void stubSubscriber() {
        AppUser user = AppUser.create("u@siot.kr", "hash");
        ReflectionTestUtils.setField(user, "id", 3L);
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        when(preferenceRepository.findByKindAndUserId(anyString(), anyLong())).thenReturn(Optional.empty());
        when(translationRepository.findByEventIdAndLanguage(anyLong(), anyString())).thenReturn(Optional.empty());
    }
}
