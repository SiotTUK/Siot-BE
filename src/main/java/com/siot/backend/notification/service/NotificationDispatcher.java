package com.siot.backend.notification.service;

import com.siot.backend.global.mail.EmailMessage;
import com.siot.backend.global.mail.EmailSender;
import com.siot.backend.member.domain.AppUser;
import com.siot.backend.member.repository.AppUserRepository;
import com.siot.backend.notification.domain.EmailDeliveryStatus;
import com.siot.backend.notification.domain.Notification;
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
                AppUser user = appUserRepository.findById(notification.getUserId()).orElse(null);
                if (user != null && user.hasVerifiedEmail()) {
                    EmailMessage message = new EmailMessage(
                            user.getEmail(),
                            notification.getTitle(),
                            notification.getBody()
                    );
                    emailSender.send(message);
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
