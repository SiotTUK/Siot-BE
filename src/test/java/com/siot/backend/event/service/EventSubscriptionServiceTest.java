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
    @org.mockito.Spy
    private java.time.Clock clock = java.time.Clock.fixed(java.time.Instant.parse("2026-11-26T03:00:00Z"),
            java.time.ZoneId.of("Asia/Seoul"));

    @InjectMocks
    private EventSubscriptionService subscriptionService;

    @Test
    @DisplayName("지운 행사는 404, 이미 끝난 행사는 400 (보낼 알림이 없다)")
    void subscribe_rejectsDeletedAndEndedEvents() {
        com.siot.backend.event.domain.Event deleted = com.siot.backend.event.domain.Event.manual(1L);
        deleted.editStartsOn(java.time.LocalDate.of(2099, 1, 1), false);
        deleted.delete(1L);
        given(eventRepository.findById(1L)).willReturn(Optional.of(deleted));
        com.siot.backend.event.domain.Event ended = com.siot.backend.event.domain.Event.manual(1L);
        ended.editStartsOn(java.time.LocalDate.of(2026, 11, 20), false);
        ended.editEndsOn(java.time.LocalDate.of(2026, 11, 25), false);
        given(eventRepository.findById(2L)).willReturn(Optional.of(ended));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> subscriptionService.subscribe(9L, 1L))
                .extracting(e -> ((com.siot.backend.global.exception.BusinessException) e).getErrorCode())
                .isEqualTo(com.siot.backend.global.exception.ErrorCode.EVENT_NOT_FOUND);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> subscriptionService.subscribe(9L, 2L))
                .extracting(e -> ((com.siot.backend.global.exception.BusinessException) e).getErrorCode())
                .isEqualTo(com.siot.backend.global.exception.ErrorCode.INVALID_INPUT);
        org.mockito.Mockito.verify(subscriptionRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("행사 시작 사전 알림 신청(구독) 검증 (NTF-02, EVE-06)")
    void subscribe_success() {
        // given
        Long userId = 1L;
        Long eventId = 10L;

        com.siot.backend.event.domain.Event event = com.siot.backend.event.domain.Event.manual(1L);
        event.editStartsOn(java.time.LocalDate.of(2099, 5, 1), false);
        given(eventRepository.findById(eventId)).willReturn(Optional.of(event));
        given(subscriptionRepository.findByUserIdAndEventId(userId, eventId))
                .willReturn(Optional.empty());
        given(subscriptionRepository.save(any(EventSubscription.class)))
                .willAnswer(inv -> inv.getArgument(0));

        // when
        EventSubscriptionResponse res = subscriptionService.subscribe(userId, eventId);

        // then
        assertThat(res.eventId()).isEqualTo(eventId);
        assertThat(res.enabled()).isTrue();
        // 신청할 때 본 일정을 기준으로 기록한다 (NTF-02 일정 변경 알림)
        verify(subscriptionRepository).save(org.mockito.ArgumentMatchers.<EventSubscription>argThat(
                s -> "2099-05-01|||".equals(s.getScheduleSnapshot())));
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
