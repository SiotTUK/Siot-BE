package com.siot.backend.notification.scheduler;

import com.siot.backend.checklist.domain.GuideTranslation;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

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

    private static final List<Integer> STAY_EXPIRY_LEAD_DAYS = List.of(120, 60, 30, 14, 7, 1);
    private static final List<Integer> REGISTRATION_LEAD_DAYS = List.of(30, 14, 7, 1);
    private static final List<Integer> TAX_LEAD_DAYS = List.of(7, 1);

    /**
     * 매일 오전 9시: 체류기간 만료, 외국인등록, 정기 세금 납부 기한 도래 알림 생성 및 발송
     */
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    public void runDailyNotificationJob() {
        log.info("Starting daily notification scheduled job...");
        generateStayExpiryNotifications();
        generateRegistrationNotifications();
        generateTaxNotifications();
        notificationDispatcher.dispatchPendingNotifications();
        log.info("Daily notification scheduled job finished.");
    }

    /**
     * 체류기간 만료 사전 알림 생성 (NTF-01, D-120, 60, 30, 14, 7, 1)
     */
    @Transactional
    public void generateStayExpiryNotifications() {
        LocalDate today = LocalDate.now();

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
     * 신규 입국자 90일 이내 외국인등록 기한 알림 생성 (CL-01, D-30, 14, 7, 1)
     */
    @Transactional
    public void generateRegistrationNotifications() {
        LocalDate today = LocalDate.now();

        for (int leadDays : REGISTRATION_LEAD_DAYS) {
            LocalDate targetEntryDate = today.minusDays(90 - leadDays);
            List<AppUser> users = appUserRepository.findByEntryDateBetween(targetEntryDate, targetEntryDate);

            for (AppUser user : users) {
                if (user.getUserType() != UserType.NEW) {
                    continue;
                }
                if (isPreferenceDisabled(user.getId(), "STAY_EXPIRY")) {
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
     * 지방세 정기 납부 일정 알림 생성 (자동차세, 재산세, 주민세 등)
     */
    @Transactional
    public void generateTaxNotifications() {
        LocalDate today = LocalDate.now();

        for (int leadDays : TAX_LEAD_DAYS) {
            LocalDate targetStartDate = today.plusDays(leadDays);
            List<CalendarSchedule> schedules = calendarScheduleRepository.findImpendingSchedules(targetStartDate, targetStartDate);

            for (CalendarSchedule schedule : schedules) {
                List<AppUser> allUsers = appUserRepository.findAll();

                for (AppUser user : allUsers) {
                    if (isPreferenceDisabled(user.getId(), "TAX")) {
                        continue;
                    }

                    // 대상 사용자 필터링
                    String scheduleKind = schedule.getKind() != null ? schedule.getKind().toUpperCase() : "";
                    if (scheduleKind.contains("CAR") && !user.hasResidence(Residence.CAR)) {
                        continue;
                    }
                    if (scheduleKind.contains("PROPERTY") && !user.hasResidence(Residence.HOUSE)) {
                        continue;
                    }

                    String sourceKey = String.format("tax:%d:%d:%d", schedule.getId(), user.getId(), leadDays);
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

                    String title = String.format("[%s] 납부 시작 D-%d 안내", scheduleTitle, leadDays);
                    String body = String.format("%s 납부 기간(%s ~ %s)이 곧 시작됩니다. 위택스(wetax.go.kr) 또는 금융기관에서 기한 내 납부해주세요.",
                            scheduleTitle, schedule.getStartsOn(), schedule.getEndsOn());
                    EmailDeliveryStatus emailStatus = user.hasVerifiedEmail() ? EmailDeliveryStatus.PENDING : null;

                    Notification notification = Notification.create(
                            user.getId(),
                            "TAX",
                            null,
                            null,
                            schedule.getId(),
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
    }

    private boolean isPreferenceDisabled(Long userId, String kind) {
        return preferenceRepository.findByKindAndUserId(kind, userId)
                .map(pref -> !pref.isEnabled())
                .orElse(false);
    }
}
