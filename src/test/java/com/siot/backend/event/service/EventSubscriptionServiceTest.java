package com.siot.backend.event.service;

import com.siot.backend.event.domain.EventSubscription;
import com.siot.backend.event.dto.EventSubscriptionResponse;
import com.siot.backend.event.repository.EventRepository;
import com.siot.backend.event.repository.EventSubscriptionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EventSubscriptionServiceTest {

    @Mock
    private EventRepository eventRepository;
    @Mock
    private EventSubscriptionRepository subscriptionRepository;

    @InjectMocks
    private EventSubscriptionService subscriptionService;

    @Test
    @DisplayName("행사 시작 사전 알림 신청(구독) 검증 (NTF-02, EVE-06)")
    void subscribe_success() {
        // given
        Long userId = 1L;
        Long eventId = 10L;

        given(eventRepository.existsById(eventId)).willReturn(true);
        given(subscriptionRepository.findByUserIdAndEventId(userId, eventId))
                .willReturn(Optional.empty());
        given(subscriptionRepository.save(any(EventSubscription.class)))
                .willAnswer(inv -> inv.getArgument(0));

        // when
        EventSubscriptionResponse res = subscriptionService.subscribe(userId, eventId);

        // then
        assertThat(res.eventId()).isEqualTo(eventId);
        assertThat(res.enabled()).isTrue();
        verify(subscriptionRepository).save(any(EventSubscription.class));
    }

    @Test
    @DisplayName("행사 시작 사전 알림 해제 검증 (NTF-03, EVE-06)")
    void unsubscribe_success() {
        // given
        Long userId = 1L;
        Long eventId = 10L;
        EventSubscription existing = EventSubscription.of(userId, eventId, true);

        given(eventRepository.existsById(eventId)).willReturn(true);
        given(subscriptionRepository.findByUserIdAndEventId(userId, eventId))
                .willReturn(Optional.of(existing));

        // when
        EventSubscriptionResponse res = subscriptionService.unsubscribe(userId, eventId);

        // then
        assertThat(res.enabled()).isFalse();
        assertThat(existing.isEnabled()).isFalse();
    }
}
