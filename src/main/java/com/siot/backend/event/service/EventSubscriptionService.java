package com.siot.backend.event.service;

import com.siot.backend.event.domain.Event;
import com.siot.backend.event.domain.EventSubscription;
import com.siot.backend.event.dto.EventSubscriptionResponse;
import com.siot.backend.event.repository.EventRepository;
import com.siot.backend.event.repository.EventSubscriptionRepository;
import com.siot.backend.global.common.PageResponse;
import com.siot.backend.global.exception.BusinessException;
import com.siot.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventSubscriptionService {

    private final EventRepository eventRepository;
    private final EventSubscriptionRepository subscriptionRepository;
    /** 한국 시간 (ClockConfig) */
    private final Clock clock;

    /**
     * 행사 시작 사전 알림 신청 (NTF-02, EVE-06). 지운 행사는 404 (회원에게 보이지 않는 행사),
     * 이미 끝난 행사는 보낼 알림이 없어 400.
     */
    @Transactional
    public EventSubscriptionResponse subscribe(Long userId, Long eventId) {
        Event event = eventRepository.findById(eventId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));
        LocalDate lastDay = event.getEndsOn() != null ? event.getEndsOn() : event.getStartsOn();
        if (lastDay != null && lastDay.isBefore(LocalDate.now(clock))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 끝난 행사는 알림을 신청할 수 없습니다.");
        }

        EventSubscription subscription = subscriptionRepository.findByUserIdAndEventId(userId, eventId)
                .map(existing -> {
                    existing.enable();
                    return existing;
                })
                .orElseGet(() -> subscriptionRepository.save(EventSubscription.of(userId, eventId, true)));
        // 신청할 때 본 일정이 기준이다. 이후 바뀌면 변경 알림을 보낸다 (NTF-02)
        subscription.recordSchedule(event.scheduleSnapshot());

        return EventSubscriptionResponse.from(subscription);
    }

    /**
     * 행사 시작 사전 알림 해제 (NTF-03, EVE-06)
     */
    @Transactional
    public EventSubscriptionResponse unsubscribe(Long userId, Long eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new BusinessException(ErrorCode.EVENT_NOT_FOUND);
        }

        EventSubscription subscription = subscriptionRepository.findByUserIdAndEventId(userId, eventId)
                .map(existing -> {
                    existing.disable();
                    return existing;
                })
                .orElseGet(() -> subscriptionRepository.save(EventSubscription.of(userId, eventId, false)));

        return EventSubscriptionResponse.from(subscription);
    }

    /**
     * 특정 행사의 알림 신청 여부 확인
     */
    public boolean isSubscribed(Long userId, Long eventId) {
        return subscriptionRepository.existsByUserIdAndEventIdAndEnabledTrue(userId, eventId);
    }

    /**
     * 로그인 회원의 알림 신청 행사 목록 조회
     */
    public PageResponse<EventSubscriptionResponse> getMySubscriptions(Long userId, Pageable pageable) {
        Page<EventSubscription> page = subscriptionRepository.findMyActive(userId, pageable);
        return PageResponse.from(page, EventSubscriptionResponse::from);
    }
}
