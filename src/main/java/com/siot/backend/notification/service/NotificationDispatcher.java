package com.siot.backend.notification.service;

import com.siot.backend.global.mail.EmailMessage;
import com.siot.backend.global.mail.EmailSender;
import com.siot.backend.member.domain.AppUser;
import com.siot.backend.member.repository.AppUserRepository;
import com.siot.backend.notification.domain.EmailDeliveryStatus;
import com.siot.backend.notification.domain.Notification;
import com.siot.backend.notification.repository.NotificationPreferenceRepository;
import com.siot.backend.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final NotificationRepository notificationRepository;
    private final AppUserRepository appUserRepository;
    private final EmailSender emailSender;
    private final NotificationPreferenceRepository preferenceRepository;

    /**
     * 알림 종류(notification.kind) → 수신 설정 종류(notification_preference.kind).
     * 외국인등록·체류지 변경 신고는 CHECKLIST_DEADLINE, 고지서 납부 마감(RUL-07)은 TAX 설정을 따른다.
     */
    static String preferenceKind(String notificationKind) {
        if (notificationKind == null) {
            return null;
        }
        return switch (notificationKind) {
            case "REGISTRATION", "RESIDENCE_REPORT" -> "CHECKLIST_DEADLINE";
            case "BILL_DUE" -> "TAX";
            default -> notificationKind;
        };
    }

    /** 발송 시점의 설정을 본다: 알림을 만든 뒤 이메일 수신을 꺼도 아직 안 보낸 메일은 보내지 않는다 */
    private boolean isEmailDisabled(Notification notification) {
        String kind = preferenceKind(notification.getKind());
        return kind != null && preferenceRepository.findByKindAndUserId(kind, notification.getUserId())
                .map(pref -> !pref.isEmailEnabled())
                .orElse(false);
    }

    /**
     * 예약 시각이 도달한 인앱 및 이메일 알림을 일괄 처리하여 발송합니다.
     */
    @Transactional
    public void dispatchPendingNotifications() {
        OffsetDateTime now = OffsetDateTime.now();

        // 1. 인앱 알림 전달 처리
        List<Notification> pendingInApp = notificationRepository.findPendingNotifications("PENDING", now, PageRequest.of(0, 100));
        for (Notification notification : pendingInApp) {
            try {
                notification.markDelivered();
            } catch (Exception e) {
                log.error("Failed to mark notification {} as delivered", notification.getId(), e);
                notification.recordDeliveryFailure(e.getMessage());
            }
        }

        // 2. 이메일 알림 발송 처리 (NTF-05)
        List<Notification> pendingEmails = notificationRepository.findPendingEmailNotifications(EmailDeliveryStatus.PENDING, now, PageRequest.of(0, 50));
        for (Notification notification : pendingEmails) {
            try {
                if (isEmailDisabled(notification)) {
                    notification.recordEmailSkipped();
                    continue;
                }
                AppUser user = appUserRepository.findById(notification.getUserId()).orElse(null);
                if (user != null && user.hasVerifiedEmail()) {
                    EmailMessage message = new EmailMessage(
                            user.getEmail(),
                            notification.getTitle(),
                            notification.getBody()
                    );
                    // 동기 발송: 실패하면 예외 → 아래에서 FAILED 로 기록 (비동기 send 는 실패를 삼켜 늘 SENT 로 기록됐다)
                    emailSender.sendNow(message);
                    notification.recordEmailSuccess();
                } else {
                    notification.recordEmailFailure("Email unverified or user not found");
                }
            } catch (Exception e) {
                log.error("Failed to send notification email for notification {}", notification.getId(), e);
                notification.recordEmailFailure(e.getMessage());
            }
        }
    }
}
