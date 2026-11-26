package com.siot.backend.notification.service;

import com.siot.backend.global.mail.EmailMessage;
import com.siot.backend.global.mail.EmailSender;
import com.siot.backend.member.domain.AppUser;
import com.siot.backend.member.repository.AppUserRepository;
import com.siot.backend.notification.domain.EmailDeliveryStatus;
import com.siot.backend.notification.domain.Notification;
import com.siot.backend.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** NTF-05 알림 메일 발송: 실제 발송 결과를 기록한다 */
@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private EmailSender emailSender;

    @InjectMocks
    private NotificationDispatcher dispatcher;

    private static Notification pendingEmail() {
        return Notification.create(1L, "TAX", null, null, null, null, "k", 1, OffsetDateTime.now().minusMinutes(1),
                "ko", "제목", "본문", EmailDeliveryStatus.PENDING);
    }

    private void verifiedUser() {
        AppUser user = AppUser.create("user@siot.kr", "hash");
        ReflectionTestUtils.setField(user, "id", 1L);
        user.markEmailVerified(OffsetDateTime.now());
        given(appUserRepository.findById(1L)).willReturn(Optional.of(user));
    }

    @Test
    void recordsFailureWhenSendingFails() {
        Notification notification = pendingEmail();
        given(notificationRepository.findPendingNotifications(anyString(), any(), any())).willReturn(List.of());
        given(notificationRepository.findPendingEmailNotifications(eq(EmailDeliveryStatus.PENDING), any(), any()))
                .willReturn(List.of(notification));
        verifiedUser();
        willThrow(new MailSendException("SMTP down")).given(emailSender).sendNow(any(EmailMessage.class));

        dispatcher.dispatchPendingNotifications();

        // 이전에는 비동기 send 가 실패를 삼켜 늘 SENT 로 기록했다
        assertThat(notification.getEmailStatus()).isEqualTo(EmailDeliveryStatus.FAILED);
        verify(emailSender, never()).send(any());
    }

    @Test
    void recordsSuccessAfterSynchronousSend() {
        Notification notification = pendingEmail();
        given(notificationRepository.findPendingNotifications(anyString(), any(), any())).willReturn(List.of());
        given(notificationRepository.findPendingEmailNotifications(eq(EmailDeliveryStatus.PENDING), any(), any()))
                .willReturn(List.of(notification));
        verifiedUser();

        dispatcher.dispatchPendingNotifications();

        verify(emailSender).sendNow(any(EmailMessage.class));
        assertThat(notification.getEmailStatus()).isEqualTo(EmailDeliveryStatus.SENT);
    }
}
