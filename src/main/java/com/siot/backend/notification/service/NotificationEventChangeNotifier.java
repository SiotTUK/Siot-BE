package com.siot.backend.notification.service;

import com.siot.backend.event.collect.EventChangeNotifier;
import com.siot.backend.event.domain.Event;
import com.siot.backend.event.domain.EventSubscription;
import com.siot.backend.event.repository.EventRepository;
import com.siot.backend.event.repository.EventSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * NTF-02 일정이 바뀐 즉시(수집·관리자 수정 커밋 뒤) 알림 신청자에게 변경 알림을 만든다.
 * 발송(인앱·이메일)은 NotificationScheduler 의 발송 작업이 한다. 놓친 변경은 매시 작업이 메운다.
 * 한 신청자의 실패는 로그만 남기고 다음 신청자를 계속한다. 이 메서드의 예외도 호출부가 잡아 행사 저장 결과는 그대로다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventChangeNotifier implements EventChangeNotifier {

    private final EventRepository eventRepository;
    private final EventSubscriptionRepository subscriptionRepository;
    private final EventChangeNotificationWriter writer;

    @Override
    public void scheduleChanged(ScheduleChange change) {
        Event event = eventRepository.findById(change.eventId())
                .filter(found -> "ACTIVE".equals(found.getStatus()))
                .orElse(null);
        if (event == null) {
            return;
        }
        // 기능 전 신청(기록 없음)도 바뀐 것이 확실하므로 바뀌기 전 일정과 비교해 알린다
        String before = Event.scheduleSnapshot(change.oldStartsOn(), change.oldEndsOn(), change.oldStartsAt(),
                change.oldEndsAt());
        int created = 0;
        for (EventSubscription subscription : subscriptionRepository.findByEventIdAndEnabledTrue(event.getId())) {
            try {
                if (writer.apply(event, subscription, before)) {
                    created++;
                }
            } catch (RuntimeException e) {
                log.warn("Event change notification failed for event {} user {}: {}", event.getId(),
                        subscription.getUserId(), e.getMessage());
            }
        }
        log.info("Event {} schedule changed, notifications created={}", event.getId(), created);
    }
}
