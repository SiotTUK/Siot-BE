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
import com.siot.backend.member.domain.AppUser;
import com.siot.backend.member.domain.Residence;
import com.siot.backend.member.domain.UserType;
import com.siot.backend.member.repository.AppUserRepository;
import com.siot.backend.notification.domain.EmailDeliveryStatus;
import com.siot.backend.notification.domain.Notification;
import com.siot.backend.notification.domain.NotificationPreference;
import com.siot.backend.notification.repository.NotificationPreferenceRepository;
import com.siot.backend.notification.repository.NotificationRepository;
import com.siot.backend.notification.service.NotificationDispatcher;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private final AppUserRepository appUserRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final CalendarScheduleRepository calendarScheduleRepository;
    private final GuideTranslationRepository guideTranslationRepository;
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

    static final String REGISTRATION_GUIDE_CODE = "CL-01";
    static final String CHECKLIST_DEADLINE_PREFERENCE = "CHECKLIST_DEADLINE";

    /**
     * 매일 오전 9시: 체류기간 만료, 외국인등록, 정기 세금 납부 기한 도래 알림 생성 및 발송
     */
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    public void runDailyNotificationJob() {
        log.info("Starting daily notification scheduled job...");
        generateStayExpiryNotifications();
        generateStayExtensionStartNotifications();
        generateRegistrationNotifications();
        generateResidenceReportNotifications();
        generateTaxNotifications();
        notificationDispatcher.dispatchPendingNotifications();
        log.info("Daily notification scheduled job finished.");
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

                String title = String.format("체류기간 만료 D-%d 안내", leadDays);
                String body = String.format("체류기간 만료일(%s)까지 %d일 남았습니다. 출입국관리사무소 또는 하이코리아에서 체류기간 연장 허가를 신청하세요.", targetDate, leadDays);
                EmailDeliveryStatus emailStatus = user.hasVerifiedEmail() ? EmailDeliveryStatus.PENDING : null;

                Notification notification = Notification.create(
                        user.getId(),
                        "STAY_EXPIRY",
                        null,
                        null,
                        null,
                        null,
                        sourceKey,
                        1,
                        OffsetDateTime.now(),
                        user.getLanguage(),
                        title,
                        body,
                        emailStatus
                );
                notificationRepository.save(notification);
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

            String title = "체류기간 연장 신청 시작 안내";
            String body = String.format("오늘부터 체류기간 연장 허가를 신청할 수 있습니다 (체류 만료일 %s). "
                    + "하이코리아(hikorea.go.kr)에서 방문 예약 또는 전자민원으로 신청하세요.", expiry);
            EmailDeliveryStatus emailStatus = user.hasVerifiedEmail() ? EmailDeliveryStatus.PENDING : null;

            notificationRepository.save(Notification.create(
                    user.getId(), "STAY_EXPIRY", null, null, null, null, sourceKey, 1,
                    OffsetDateTime.now(clock), user.getLanguage(), title, body, emailStatus));
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
                String title = String.format("외국인등록 마감 D-%d 안내", leadDays);
                String body = String.format("입국 후 90일 외국인등록 마감일(%s)까지 %d일 남았습니다. 관할 출입국·외국인관서에 방문하여 등록을 완료해주세요.", deadline, leadDays);
                EmailDeliveryStatus emailStatus = user.hasVerifiedEmail() ? EmailDeliveryStatus.PENDING : null;

                Notification notification = Notification.create(
                        user.getId(),
                        "REGISTRATION",
                        null,
                        null,
                        null,
                        null,
                        sourceKey,
                        1,
                        OffsetDateTime.now(),
                        user.getLanguage(),
                        title,
                        body,
                        emailStatus
                );
                notificationRepository.save(notification);
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

                boolean overseasKorean = ResidenceChangeReport.OVERSEAS_KOREAN_REPORT_CODE.equals(reportCode);
                String reportName = overseasKorean ? "거소 이전 신고" : "체류지 변경 신고";
                String title = String.format("%s 마감 D-%d 안내", reportName, leadDays);
                String body = String.format("이사일(%s)부터 %d일 이내인 %s 마감일(%s)까지 %d일 남았습니다. "
                                + "새 주소지 주민센터나 출입국·외국인관서, 하이코리아에서 신고해주세요.",
                        move.getBaseDate(), ChronoUnit.DAYS.between(move.getBaseDate(), deadline), reportName, deadline, leadDays);
                EmailDeliveryStatus emailStatus = user.hasVerifiedEmail() ? EmailDeliveryStatus.PENDING : null;

                notificationRepository.save(Notification.create(
                        user.getId(), "RESIDENCE_REPORT", null, guide != null ? guide.getId() : null, null, null,
                        sourceKey, 1, OffsetDateTime.now(clock), user.getLanguage(), title, body, emailStatus));
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
                    title -> String.format("[%s] 오늘부터 납부 기간입니다", title),
                    title -> String.format("%s 납부 기간(%s ~ %s)이 오늘 시작됐습니다. 위택스(wetax.go.kr) 또는 금융기관에서 기한 내 납부해주세요.",
                            title, schedule.getStartsOn(), schedule.getEndsOn()));
        }

        // 납부 마감 D-7, D-1
        for (int leadDays : TAX_LEAD_DAYS) {
            for (CalendarSchedule schedule : calendarScheduleRepository.findByEndsOnAndActiveTrue(today.plusDays(leadDays))) {
                notifyTaxSchedule(schedule, "due:" + leadDays,
                        title -> String.format("[%s] 납부 마감 D-%d 안내", title, leadDays),
                        title -> String.format("%s 납부 마감일(%s)까지 %d일 남았습니다. 위택스(wetax.go.kr) 또는 금융기관에서 기한 내 납부해주세요.",
                                title, schedule.getEndsOn(), leadDays));
            }
        }
    }

    /** 납부 대상 회원에게 세금 알림을 한 번씩 만든다. sourceKeyPart 로 시작일·마감 D-n 알림을 구분한다 */
    private void notifyTaxSchedule(CalendarSchedule schedule, String sourceKeyPart,
                                   Function<String, String> titleOf, Function<String, String> bodyOf) {
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
                        .map(GuideTranslation::getTitle)
                        .orElse(schedule.getKind());
            }
            EmailDeliveryStatus emailStatus = user.hasVerifiedEmail() ? EmailDeliveryStatus.PENDING : null;

            notificationRepository.save(Notification.create(
                    user.getId(), "TAX", null, null, schedule.getId(), null, sourceKey, 1,
                    OffsetDateTime.now(clock), user.getLanguage(),
                    titleOf.apply(scheduleTitle), bodyOf.apply(scheduleTitle), emailStatus));
        }
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
