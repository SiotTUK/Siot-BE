package com.siot.backend.notification.scheduler;

import com.siot.backend.checklist.domain.ChecklistProgress;
import com.siot.backend.checklist.domain.Guide;
import com.siot.backend.checklist.domain.GuideTranslation;
import com.siot.backend.checklist.domain.MemberLifeChange;
import com.siot.backend.checklist.domain.ProgressStatus;
import com.siot.backend.checklist.domain.ResidenceChangeReport;
import com.siot.backend.checklist.domain.StayExtension;
import com.siot.backend.checklist.repository.ChecklistProgressRepository;
import com.siot.backend.checklist.repository.GuideRepository;
import com.siot.backend.checklist.repository.MemberLifeChangeRepository;
import com.siot.backend.checklist.repository.GuideTranslationRepository;
import com.siot.backend.event.domain.Event;
import com.siot.backend.event.domain.EventSubscription;
import com.siot.backend.event.domain.EventTranslation;
import com.siot.backend.event.repository.EventRepository;
import com.siot.backend.event.repository.EventSubscriptionRepository;
import com.siot.backend.event.repository.EventTranslationRepository;
import com.siot.backend.member.domain.AppUser;
import com.siot.backend.member.domain.Residence;
import com.siot.backend.member.domain.UserType;
import com.siot.backend.member.repository.AppUserRepository;
import com.siot.backend.notification.domain.EmailDeliveryStatus;
import com.siot.backend.notification.domain.Notification;
import com.siot.backend.notification.repository.NotificationPreferenceRepository;
import com.siot.backend.notification.repository.NotificationRepository;
import com.siot.backend.notification.service.NotificationDispatcher;
import com.siot.backend.notification.service.NotificationMessages;
import com.siot.backend.schedule.domain.CalendarSchedule;
import com.siot.backend.schedule.repository.CalendarScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private final AppUserRepository appUserRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final CalendarScheduleRepository calendarScheduleRepository;
    private final GuideTranslationRepository guideTranslationRepository;
    private final EventRepository eventRepository;
    private final EventSubscriptionRepository eventSubscriptionRepository;
    private final EventTranslationRepository eventTranslationRepository;
    private final NotificationDispatcher notificationDispatcher;
    private final GuideRepository guideRepository;
    private final ChecklistProgressRepository checklistProgressRepository;
    private final MemberLifeChangeRepository memberLifeChangeRepository;
    /** 한국 시간 (ClockConfig) */
    private final Clock clock;

    private static final List<Integer> STAY_EXPIRY_LEAD_DAYS = List.of(120, 60, 30, 14, 7, 1);
    /** NTF-08: 외국인등록 마감 D-7, D-1 */
    private static final List<Integer> REGISTRATION_LEAD_DAYS = List.of(7, 1);
    /** NTF-08: 체류지 변경 신고 마감 D-7, D-1 */
    private static final List<Integer> RESIDENCE_REPORT_LEAD_DAYS = List.of(7, 1);
    /** NTF-01: 납부 마감 D-7, D-1 (납부 시작일 당일 알림은 따로 보낸다) */
    private static final List<Integer> TAX_LEAD_DAYS = List.of(7, 1);
    /** NTF-02: 시각을 모르는 행사의 시작 전날 알림은 이 시각(한국 시간) 이후에 보낸다 (자정에 보내지 않게) */
    private static final int DATE_ONLY_EVENT_NOTIFY_HOUR = 9;
    /** NTF-02 행사 시작 알림을 고를 수 있는 가장 이른 시점 (EventSubscription.LEAD_DAYS_OPTIONS 최댓값) */
    static final int EVENT_MAX_LEAD_DAYS = 7;

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    static final String REGISTRATION_GUIDE_CODE = "CL-01";
    static final String CHECKLIST_DEADLINE_PREFERENCE = "CHECKLIST_DEADLINE";

    /**
     * 매일 오전 9시: 체류기간 만료, 외국인등록, 체류지 변경 신고, 정기 세금 납부 알림 생성 및 발송
     */
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    public void runDailyNotificationJob() {
        log.info("Starting daily notification scheduled job...");
        generateStayExpiryNotifications();
        generateStayExtensionStartNotifications();
        generateRegistrationNotifications();
        generateResidenceReportNotifications();
        generateTaxNotifications();
        log.info("Daily notification scheduled job finished.");
    }

    /**
     * 매시 정각: 알림을 신청한 행사의 시작 24시간 전, 신청 마감 D-1(24시간 전), 일정 변경 알림 (NTF-02)
     */
    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    public void runHourlyEventNotificationJob() {
        generateEventNotifications();
    }

    /**
     * NTF-05 발송: 설정 주기(기본 1분)마다 발송 대기 알림을 보낸다. 알림을 만드는 작업(위 두 개, 관리자 안내 변경)과
     * 발송을 나눠, 관리자가 안내를 바꿔 알림이 많이 생겨도 다음 주기에 이어서 보낸다.
     * 발송은 이 작업에서만 한다: 스케줄러 스레드가 2개라 다른 작업에서도 부르면 같은 메일을 동시에 두 번 보낼 수 있다.
     */
    @Scheduled(fixedDelayString = "${siot.scheduling.notification-dispatch-interval}",
            initialDelayString = "${siot.scheduling.notification-dispatch-interval}")
    public void runDispatchJob() {
        notificationDispatcher.dispatchPendingNotifications();
    }

    /**
     * 체류기간 만료 사전 알림 생성 (NTF-01, D-120, 60, 30, 14, 7, 1)
     */
    @Transactional
    public void generateStayExpiryNotifications() {
        LocalDate today = LocalDate.now(clock);

        for (int leadDays : STAY_EXPIRY_LEAD_DAYS) {
            LocalDate targetDate = today.plusDays(leadDays);
            List<AppUser> users = appUserRepository.findImpendingStayUsers(targetDate, targetDate);

            for (AppUser user : users) {
                if (isPreferenceDisabled(user.getId(), "STAY_EXPIRY")) {
                    continue;
                }

                String sourceKey = String.format("stay_expiry:%d:%d:%s", user.getId(), leadDays, targetDate);
                if (notificationRepository.existsByUserIdAndSourceKey(user.getId(), sourceKey)) {
                    continue;
                }

                NotificationMessages m = NotificationMessages.forLanguage(user.getLanguage());
                save(user, "STAY_EXPIRY", null, null, null, sourceKey,
                        String.format(m.stayExpiryTitle(), leadDays),
                        String.format(m.stayExpiryBody(), targetDate, leadDays));
            }
        }
    }

    /**
     * 체류기간 연장 신청 시작일 알림 (NTF-01). 신청 시작일 = 만료일 − 4개월 (CHK-10).
     * 그달에 같은 날짜가 없으면 말일이므로 (예: 6/30 만료 → 2/28), 만료일이 오늘+4개월 ~ +3일인 회원을 찾아 다시 계산해 거른다.
     */
    @Transactional
    public void generateStayExtensionStartNotifications() {
        LocalDate today = LocalDate.now(clock);
        LocalDate from = today.plusMonths(StayExtension.APPLY_MONTHS_BEFORE_EXPIRY);
        List<AppUser> users = appUserRepository.findImpendingStayUsers(from, from.plusDays(3));

        for (AppUser user : users) {
            LocalDate expiry = user.getStayExpiry();
            if (expiry == null || !today.equals(StayExtension.applyStartDate(expiry))) {
                continue;
            }
            if (isPreferenceDisabled(user.getId(), "STAY_EXPIRY")) {
                continue;
            }

            String sourceKey = String.format("stay_extension_start:%d:%s", user.getId(), expiry);
            if (notificationRepository.existsByUserIdAndSourceKey(user.getId(), sourceKey)) {
                continue;
            }

            NotificationMessages m = NotificationMessages.forLanguage(user.getLanguage());
            save(user, "STAY_EXPIRY", null, null, null, sourceKey,
                    m.stayExtensionStartTitle(), String.format(m.stayExtensionStartBody(), expiry));
        }
    }

    /**
     * 외국인등록 마감 알림 (NTF-08, CL-01, D-7·D-1). 체크리스트에서 완료·해당 없음으로 표시한 회원은 제외한다.
     */
    @Transactional
    public void generateRegistrationNotifications() {
        LocalDate today = LocalDate.now(clock);
        Optional<Long> registrationGuideId = guideRepository.findByCode(REGISTRATION_GUIDE_CODE).map(Guide::getId);

        for (int leadDays : REGISTRATION_LEAD_DAYS) {
            LocalDate targetEntryDate = today.minusDays(90 - leadDays);
            List<AppUser> users = appUserRepository.findByEntryDateBetween(targetEntryDate, targetEntryDate);

            for (AppUser user : users) {
                if (user.getUserType() != UserType.NEW) {
                    continue;
                }
                if (isPreferenceDisabled(user.getId(), CHECKLIST_DEADLINE_PREFERENCE)) {
                    continue;
                }
                if (registrationGuideId.isPresent() && isChecklistClosed(user.getId(), registrationGuideId.get())) {
                    continue;
                }

                String sourceKey = String.format("registration:%d:%d:%s", user.getId(), leadDays, targetEntryDate);
                if (notificationRepository.existsByUserIdAndSourceKey(user.getId(), sourceKey)) {
                    continue;
                }

                LocalDate deadline = user.getEntryDate().plusDays(90);
                NotificationMessages m = NotificationMessages.forLanguage(user.getLanguage());
                // 알림을 누르면 외국인등록 항목 상세로 간다 (NTF-04)
                save(user, "REGISTRATION", null, registrationGuideId.orElse(null), null, sourceKey,
                        String.format(m.registrationTitle(), leadDays),
                        String.format(m.registrationBody(), deadline, leadDays));
            }
        }
    }

    /**
     * 체류지 변경 신고 마감 알림 (NTF-08, CHK-02, D-7·D-1).
     * 이사(생활 변화 LC-01)를 등록한 회원에게, 체류지 변경 신고(이사일 + 15일) 또는
     * F-4 거소 이전 신고(이사일 + 14일) 마감 전에 알린다. 그 회차를 완료·해당 없음으로 표시했으면 제외한다.
     */
    @Transactional
    public void generateResidenceReportNotifications() {
        LocalDate today = LocalDate.now(clock);

        for (int leadDays : RESIDENCE_REPORT_LEAD_DAYS) {
            LocalDate targetDeadline = today.plusDays(leadDays);
            List<MemberLifeChange> moves = memberLifeChangeRepository.findByLifeChangeCodeAndBaseDateBetween(
                    ResidenceChangeReport.LIFE_CHANGE_CODE,
                    ResidenceChangeReport.earliestMoveDateFor(targetDeadline),
                    ResidenceChangeReport.latestMoveDateFor(targetDeadline));

            for (MemberLifeChange move : moves) {
                AppUser user = appUserRepository.findById(move.getUserId()).orElse(null);
                if (user == null) {
                    continue;
                }
                String reportCode = ResidenceChangeReport.reportCodeFor(user.hasResidence(Residence.F4));
                LocalDate deadline = ResidenceChangeReport.deadline(reportCode, move.getBaseDate());
                if (!deadline.equals(targetDeadline)) {
                    continue; // 범위에는 걸렸지만 이 회원의 기한(14일/15일)과 다름
                }
                if (isPreferenceDisabled(user.getId(), CHECKLIST_DEADLINE_PREFERENCE)) {
                    continue;
                }
                Guide guide = guideRepository.findByCode(reportCode).orElse(null);
                String periodKey = move.getBaseDate().toString(); // 생활 변화 할 일의 회차 키 = 기준일
                if (guide != null && isChecklistClosed(user.getId(), guide.getId(), periodKey)) {
                    continue;
                }

                String sourceKey = String.format("residence_report:%d:%s:%d", user.getId(), move.getBaseDate(), leadDays);
                if (notificationRepository.existsByUserIdAndSourceKey(user.getId(), sourceKey)) {
                    continue;
                }

                NotificationMessages m = NotificationMessages.forLanguage(user.getLanguage());
                boolean overseasKorean = ResidenceChangeReport.OVERSEAS_KOREAN_REPORT_CODE.equals(reportCode);
                String reportName = overseasKorean ? m.overseasKoreanReportName() : m.residenceReportName();
                save(user, "RESIDENCE_REPORT", null, guide != null ? guide.getId() : null, null, sourceKey,
                        String.format(m.residenceReportTitle(), reportName, leadDays),
                        String.format(m.residenceReportBody(), move.getBaseDate(),
                                ChronoUnit.DAYS.between(move.getBaseDate(), deadline), reportName, deadline, leadDays));
            }
        }
    }

    /**
     * 지방세 정기 납부 알림 (NTF-01): 납부 시작일, 납부 마감 D-7·D-1 (자동차세, 재산세, 주민세 등)
     */
    @Transactional
    public void generateTaxNotifications() {
        LocalDate today = LocalDate.now(clock);

        // 납부 시작일 (당일)
        for (CalendarSchedule schedule : calendarScheduleRepository.findImpendingSchedules(today, today)) {
            notifyTaxSchedule(schedule, "start",
                    (m, title) -> String.format(m.taxStartTitle(), title),
                    (m, title) -> String.format(m.taxStartBody(), title, schedule.getStartsOn(), schedule.getEndsOn()));
        }

        // 납부 마감 D-7, D-1
        for (int leadDays : TAX_LEAD_DAYS) {
            for (CalendarSchedule schedule : calendarScheduleRepository.findByEndsOnAndActiveTrue(today.plusDays(leadDays))) {
                notifyTaxSchedule(schedule, "due:" + leadDays,
                        (m, title) -> String.format(m.taxDueTitle(), title, leadDays),
                        (m, title) -> String.format(m.taxDueBody(), title, schedule.getEndsOn(), leadDays));
            }
        }
    }

    /**
     * 알림을 신청한 행사 알림 (NTF-02, EVE-06). 매시 실행한다.
     * <ul>
     *   <li>행사 시작 24시간 전: 시작 시각을 알면 시작 24시간 안에 들어온 첫 실행에, 모르면 시작 전날 오전 9시 이후 첫 실행에 보낸다</li>
     *   <li>신청 마감 D-1: 신청 마감 시각이 있는 행사만, 마감 24시간 안에 들어온 첫 실행에 보낸다</li>
     *   <li>일정 변경: 신청할 때(또는 마지막 변경 안내 때) 기록한 일정과 지금 일정이 다르면 보낸다</li>
     * </ul>
     */
    @Transactional
    public void generateEventNotifications() {
        ZonedDateTime now = ZonedDateTime.now(clock);
        LocalDate today = now.toLocalDate();

        // 1. 행사 시작 전: 신청한 시점(leadDays)마다. 1 = 시작 24시간 전, 3·7 = 시작 N일 전 오전 9시 이후
        for (Event event : eventRepository.findByStartsOnBetweenAndStatus(today, today.plusDays(EVENT_MAX_LEAD_DAYS), "ACTIVE")) {
            String startText = event.getStartsAt() != null && !event.isTimeUnknown()
                    ? event.getStartsOn() + " " + event.getStartsAt().format(TIME)
                    : event.getStartsOn().toString();
            boolean dayBeforeDue = isStartAlertDue(event, now);
            notifyEventSubscribers(event,
                    sub -> sub.getLeadDays() <= EventSubscription.DEFAULT_LEAD_DAYS ? dayBeforeDue
                            : event.getStartsOn().equals(today.plusDays(sub.getLeadDays()))
                            && now.getHour() >= DATE_ONLY_EVENT_NOTIFY_HOUR,
                    sub -> sub.getLeadDays() <= EventSubscription.DEFAULT_LEAD_DAYS
                            ? "event_start:%d:%d:" + event.getStartsOn()
                            : "event_start:%d:%d:" + event.getStartsOn() + ":d" + sub.getLeadDays(),
                    (m, title) -> String.format(m.eventStartTitle(), title),
                    (m, title) -> String.format(m.eventStartBody(), title, startText));
        }

        // 2. 신청 마감 D-1 (마감 24시간 전)
        for (Event event : eventRepository.findByStatusAndApplicationDeadlineBetween(
                "ACTIVE", now.toOffsetDateTime(), now.plusHours(24).toOffsetDateTime())) {
            OffsetDateTime deadline = event.getApplicationDeadline();
            String deadlineText = deadline.atZoneSameInstant(clock.getZone()).format(DATE_TIME);
            notifyEventSubscribers(event, "event_deadline:%d:%d:" + deadline.toEpochSecond(),
                    (m, title) -> String.format(m.eventDeadlineTitle(), title),
                    (m, title) -> String.format(m.eventDeadlineBody(), title, deadlineText));
        }

        // 3. 일정 변경
        generateEventScheduleChangeNotifications(today);
    }

    /** 시작 시각을 알면 (지금, 지금+24시간] 안에 시작할 때, 모르면 내일 시작이고 지금이 오전 9시 이후일 때 */
    private boolean isStartAlertDue(Event event, ZonedDateTime now) {
        if (event.getStartsAt() != null && !event.isTimeUnknown()) {
            ZonedDateTime start = event.getStartsOn().atTime(event.getStartsAt()).atZone(clock.getZone());
            return start.isAfter(now) && !start.isAfter(now.plusHours(24));
        }
        return event.getStartsOn().equals(now.toLocalDate().plusDays(1))
                && now.getHour() >= DATE_ONLY_EVENT_NOTIFY_HOUR;
    }

    /** 끝나지 않은 행사의 알림 신청 중, 기록한 일정과 지금 일정이 다른 것에 변경 알림을 보내고 기록을 갱신한다 */
    private void generateEventScheduleChangeNotifications(LocalDate today) {
        List<EventSubscription> subscriptions = eventSubscriptionRepository.findEnabledForCurrentEvents(today);
        if (subscriptions.isEmpty()) {
            return;
        }
        Map<Long, Event> events = eventRepository.findAllById(
                        subscriptions.stream().map(EventSubscription::getEventId).distinct().toList())
                .stream().collect(Collectors.toMap(Event::getId, Function.identity()));

        for (EventSubscription subscription : subscriptions) {
            Event event = events.get(subscription.getEventId());
            if (event == null) {
                continue;
            }
            String current = event.scheduleSnapshot();
            String recorded = subscription.getScheduleSnapshot();
            if (Objects.equals(current, recorded)) {
                continue;
            }
            // 기록이 없으면 (이 기능 전 신청) 알림 없이 지금 일정을 기준으로 삼는다
            if (recorded != null) {
                AppUser user = appUserRepository.findById(subscription.getUserId()).orElse(null);
                if (user != null && !isPreferenceDisabled(user.getId(), "EVENT")) {
                    String sourceKey = String.format("event_changed:%d:%d:%s", event.getId(), user.getId(), current);
                    if (!notificationRepository.existsByUserIdAndSourceKey(user.getId(), sourceKey)) {
                        NotificationMessages m = NotificationMessages.forLanguage(user.getLanguage());
                        String title = eventTitle(event, user, m);
                        save(user, "EVENT", event.getId(), null, null, sourceKey,
                                String.format(m.eventChangedTitle(), title),
                                String.format(m.eventChangedBody(), title, scheduleText(event)));
                    }
                }
            }
            subscription.recordSchedule(current);
            eventSubscriptionRepository.save(subscription);
        }
    }

    /** "2026-12-01 14:00 ~ 2026-12-03 18:00" (없는 값은 빼고) */
    private static String scheduleText(Event event) {
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

    private static String dateTimeText(LocalDate date, java.time.LocalTime time) {
        if (date == null) {
            return "";
        }
        return time == null ? date.toString() : date + " " + time.format(TIME);
    }

    /**
     * 행사 알림을 켠 회원에게 한 번씩 알림을 만든다.
     *
     * @param sourceKeyFormat 행사 ID, 회원 ID 순으로 채울 형식 (예: "event_start:%d:%d:2026-12-01")
     */
    private void notifyEventSubscribers(Event event, String sourceKeyFormat,
                                        BiFunction<NotificationMessages, String, String> titleOf,
                                        BiFunction<NotificationMessages, String, String> bodyOf) {
        notifyEventSubscribers(event, sub -> true, sub -> sourceKeyFormat, titleOf, bodyOf);
    }

    /**
     * @param due             이 신청자에게 지금 보낼지 (알림 시점 leadDays 로 거른다)
     * @param sourceKeyFormat 신청별 sourceKey 형식
     */
    private void notifyEventSubscribers(Event event, java.util.function.Predicate<EventSubscription> due,
                                        Function<EventSubscription, String> sourceKeyFormat,
                                        BiFunction<NotificationMessages, String, String> titleOf,
                                        BiFunction<NotificationMessages, String, String> bodyOf) {
        for (EventSubscription sub : eventSubscriptionRepository.findByEventIdAndEnabledTrue(event.getId())) {
            Long userId = sub.getUserId();
            if (!due.test(sub)) {
                continue;
            }
            if (isPreferenceDisabled(userId, "EVENT")) {
                continue;
            }
            String sourceKey = String.format(sourceKeyFormat.apply(sub), event.getId(), userId);
            if (notificationRepository.existsByUserIdAndSourceKey(userId, sourceKey)) {
                continue;
            }
            AppUser user = appUserRepository.findById(userId).orElse(null);
            if (user == null) {
                continue;
            }
            NotificationMessages m = NotificationMessages.forLanguage(user.getLanguage());
            String title = eventTitle(event, user, m);
            save(user, "EVENT", event.getId(), null, null, sourceKey, titleOf.apply(m, title), bodyOf.apply(m, title));
        }
    }

    /** 회원 언어의 행사명, 없으면 한국어, 그것도 없으면 기본 문구 */
    private String eventTitle(Event event, AppUser user, NotificationMessages m) {
        return eventTranslationRepository.findByEventIdAndLanguage(event.getId(), user.getLanguage())
                .or(() -> eventTranslationRepository.findByEventIdAndLanguage(event.getId(), "ko"))
                .map(EventTranslation::getTitle)
                .orElse(m.defaultEventTitle());
    }

    /** 납부 대상 회원에게 세금 알림을 한 번씩 만든다. sourceKeyPart 로 시작일·마감 D-n 알림을 구분한다 */
    private void notifyTaxSchedule(CalendarSchedule schedule, String sourceKeyPart,
                                   BiFunction<NotificationMessages, String, String> titleOf,
                                   BiFunction<NotificationMessages, String, String> bodyOf) {
        String scheduleKind = schedule.getKind() != null ? schedule.getKind().toUpperCase() : "";

        for (AppUser user : appUserRepository.findAll()) {
            if (isPreferenceDisabled(user.getId(), "TAX")) {
                continue;
            }
            // 대상 사용자 필터링
            if (scheduleKind.contains("CAR") && !user.hasResidence(Residence.CAR)) {
                continue;
            }
            if (scheduleKind.contains("PROPERTY") && !user.hasResidence(Residence.HOUSE)) {
                continue;
            }

            String sourceKey = String.format("tax:%s:%d:%d", sourceKeyPart, schedule.getId(), user.getId());
            if (notificationRepository.existsByUserIdAndSourceKey(user.getId(), sourceKey)) {
                continue;
            }

            String scheduleTitle = schedule.getKind();
            if (schedule.getGuideId() != null) {
                scheduleTitle = guideTranslationRepository
                        .findByGuideIdAndLanguage(schedule.getGuideId(), user.getLanguage())
                        .or(() -> guideTranslationRepository.findByGuideIdAndLanguage(schedule.getGuideId(), "ko"))
                        .map(GuideTranslation::getTitle)
                        .orElse(schedule.getKind());
            }
            NotificationMessages m = NotificationMessages.forLanguage(user.getLanguage());
            save(user, "TAX", null, null, schedule.getId(), sourceKey,
                    titleOf.apply(m, scheduleTitle), bodyOf.apply(m, scheduleTitle));
        }
    }

    /** 알림을 저장한다. 인증된 이메일이 있으면 메일 발송 대기로 둔다 (NTF-05, NTF-09) */
    private void save(AppUser user, String kind, Long eventId, Long guideId, Long scheduleId, String sourceKey,
                      String title, String body) {
        EmailDeliveryStatus emailStatus = user.hasVerifiedEmail() ? EmailDeliveryStatus.PENDING : null;
        notificationRepository.save(Notification.create(
                user.getId(), kind, eventId, guideId, scheduleId, null, sourceKey, 1,
                OffsetDateTime.now(clock), user.getLanguage(), title, body, emailStatus));
    }

    /** 완료(DONE) 또는 해당 없음(NOT_APPLICABLE)으로 표시한 1회성 항목이면 true */
    private boolean isChecklistClosed(Long userId, Long guideId) {
        return isChecklistClosed(userId, guideId, ChecklistProgress.ONCE);
    }

    /** 그 회차(periodKey)를 완료 또는 해당 없음으로 표시했으면 true */
    private boolean isChecklistClosed(Long userId, Long guideId, String periodKey) {
        return checklistProgressRepository
                .findByUserIdAndGuideIdAndPeriodKey(userId, guideId, periodKey)
                .map(p -> p.getStatus() == ProgressStatus.DONE || p.getStatus() == ProgressStatus.NOT_APPLICABLE)
                .orElse(false);
    }

    private boolean isPreferenceDisabled(Long userId, String kind) {
        return preferenceRepository.findByKindAndUserId(kind, userId)
                .map(pref -> !pref.isEnabled())
                .orElse(false);
    }
}
