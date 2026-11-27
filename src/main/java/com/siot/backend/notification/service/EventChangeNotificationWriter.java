package com.siot.backend.notification.service;

import com.siot.backend.event.collect.EventVisibilityProperties;
import com.siot.backend.event.domain.Event;
import com.siot.backend.event.domain.EventSubscription;
import com.siot.backend.event.domain.EventTranslation;
import com.siot.backend.event.repository.EventSubscriptionRepository;
import com.siot.backend.event.repository.EventTranslationRepository;
import com.siot.backend.member.domain.AppUser;
import com.siot.backend.member.repository.AppUserRepository;
import com.siot.backend.notification.domain.EmailDeliveryStatus;
import com.siot.backend.notification.domain.Notification;
import com.siot.backend.notification.repository.NotificationPreferenceRepository;
import com.siot.backend.notification.repository.NotificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * NTF-02 행사 일정 변경 알림을 한 신청자에게 만든다. 두 경로가 같이 쓴다.
 * <ul>
 *   <li>즉시: 수집·관리자 수정 커밋 뒤 {@link NotificationEventChangeNotifier}</li>
 *   <li>보완: 매시 NotificationScheduler (즉시 경로가 실패했거나 놓친 변경)</li>
 * </ul>
 * 규칙: 기록한 일정(event_subscription.schedule_snapshot)과 지금 일정이 다를 때만, 행사 알림 설정을 끈 회원은 빼고,
 * 회원 언어로, 중복 방지 키 {@code event_changed:{eventId}:{userId}:{지금 일정}}. 알림을 만들었든 아니든 기록을 지금 일정으로 바꾼다.
 * 공개하지 않는 지역의 행사는 알림도 만들지 않고 기록도 바꾸지 않는다: 다시 공개하면 마지막으로 안내한 일정과 비교해 알린다.
 * 신청자마다 새 트랜잭션에서 처리한다: 두 경로가 같은 키를 동시에 넣어 유니크 제약에 걸려도 그 신청자만 건너뛰고 나머지는 계속한다.
 */
@Slf4j
@Component
public class EventChangeNotificationWriter {

    static final String KIND = "EVENT";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final EventSubscriptionRepository subscriptionRepository;
    private final EventTranslationRepository translationRepository;
    private final AppUserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final Clock clock;
    private final EventVisibilityProperties visibility;
    private final TransactionOperations perSubscriber;

    @Autowired
    public EventChangeNotificationWriter(EventSubscriptionRepository subscriptionRepository,
                                         EventTranslationRepository translationRepository,
                                         AppUserRepository userRepository,
                                         NotificationRepository notificationRepository,
                                         NotificationPreferenceRepository preferenceRepository,
                                         Clock clock,
                                         EventVisibilityProperties visibility,
                                         PlatformTransactionManager transactionManager) {
        this(subscriptionRepository, translationRepository, userRepository, notificationRepository,
                preferenceRepository, clock, visibility, requiresNew(transactionManager));
    }

    /** 테스트용: 트랜잭션 처리를 바꿔 끼운다 */
    public EventChangeNotificationWriter(EventSubscriptionRepository subscriptionRepository,
                                         EventTranslationRepository translationRepository,
                                         AppUserRepository userRepository,
                                         NotificationRepository notificationRepository,
                                         NotificationPreferenceRepository preferenceRepository,
                                         Clock clock,
                                         EventVisibilityProperties visibility,
                                         TransactionOperations perSubscriber) {
        this.subscriptionRepository = subscriptionRepository;
        this.translationRepository = translationRepository;
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.preferenceRepository = preferenceRepository;
        this.clock = clock;
        this.visibility = visibility;
        this.perSubscriber = perSubscriber;
    }

    private static TransactionOperations requiresNew(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    /**
     * @param baselineIfUnrecorded 기록한 일정이 없을 때(이 기능 전 신청) 비교할 일정. null 이면 알림 없이 지금 일정을 기록만 한다
     *                             (매시 경로). 즉시 경로는 바뀌기 전 일정을 넘겨 알림을 보낸다
     * @return 알림을 새로 만들었으면 true
     */
    public boolean apply(Event event, EventSubscription subscription, String baselineIfUnrecorded) {
        if (!visibility.inPublicRegion(event)) {
            return false;
        }
        String current = event.scheduleSnapshot();
        String recorded = subscription.getScheduleSnapshot() != null
                ? subscription.getScheduleSnapshot() : baselineIfUnrecorded;
        if (Objects.equals(current, subscription.getScheduleSnapshot())) {
            return false;
        }
        try {
            Boolean created = perSubscriber.execute(status -> {
                boolean made = !Objects.equals(current, recorded) && recorded != null && create(event, subscription, current);
                subscription.recordSchedule(current);
                subscriptionRepository.save(subscription);
                return made;
            });
            return Boolean.TRUE.equals(created);
        } catch (DataIntegrityViolationException e) {
            // 다른 경로가 같은 변경 알림을 먼저 만들었다 (uk_notification_user_source)
            log.debug("Event change notification already exists for event {} user {}", event.getId(),
                    subscription.getUserId());
            return false;
        }
    }

    private boolean create(Event event, EventSubscription subscription, String current) {
        AppUser user = userRepository.findById(subscription.getUserId()).orElse(null);
        if (user == null || isEventPreferenceDisabled(user.getId())) {
            return false;
        }
        String sourceKey = String.format("event_changed:%d:%d:%s", event.getId(), user.getId(), current);
        if (notificationRepository.existsByUserIdAndSourceKey(user.getId(), sourceKey)) {
            return false;
        }
        NotificationMessages m = NotificationMessages.forLanguage(user.getLanguage());
        String title = eventTitle(event, user.getLanguage(), m);
        EmailDeliveryStatus emailStatus = user.hasVerifiedEmail() ? EmailDeliveryStatus.PENDING : null;
        notificationRepository.saveAndFlush(Notification.create(
                user.getId(), KIND, event.getId(), null, null, null, sourceKey, 1,
                OffsetDateTime.now(clock), user.getLanguage(),
                String.format(m.eventChangedTitle(), title),
                String.format(m.eventChangedBody(), title, scheduleText(event)),
                emailStatus));
        return true;
    }

    private boolean isEventPreferenceDisabled(Long userId) {
        return preferenceRepository.findByKindAndUserId(KIND, userId)
                .map(pref -> !pref.isEnabled())
                .orElse(false);
    }

    /** 회원 언어의 행사명, 없으면 한국어, 그것도 없으면 기본 문구 */
    public String eventTitle(Event event, String language, NotificationMessages m) {
        return translationRepository.findByEventIdAndLanguage(event.getId(), language)
                .or(() -> translationRepository.findByEventIdAndLanguage(event.getId(), "ko"))
                .map(EventTranslation::getTitle)
                .orElse(m.defaultEventTitle());
    }

    /** "2026-12-01 14:00 ~ 2026-12-03 18:00" (없는 값은 빼고) */
    static String scheduleText(Event event) {
        String start = dateTimeText(event.getStartsOn(), event.isTimeUnknown() ? null : event.getStartsAt());
        String end = dateTimeText(event.getEndsOn(), event.isTimeUnknown() ? null : event.getEndsAt());
        if (start.isEmpty()) {
            return end;
        }
        if (end.isEmpty() || end.equals(start)) {
            return start;
        }
        return start + " ~ " + end;
    }

    private static String dateTimeText(LocalDate date, LocalTime time) {
        if (date == null) {
            return "";
        }
        return time == null ? date.toString() : date + " " + time.format(TIME);
    }
}
