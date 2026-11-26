package com.siot.backend.global.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * SMTP 메일 발송 (운영: Gmail SMTP, 로컬: Mailpit).
 * Gmail 무료 계정은 하루 약 500통으로 발송량이 제한된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;

    @Async
    @Override
    public void send(EmailMessage message) {
        try {
            sendNow(message);
        } catch (MailException e) {
            // 수신자 주소는 개인정보라 로그에 남기지 않는다
            log.error("Mail send failed: subject='{}'", message.subject(), e);
        }
    }

    /** 동기로 보낸다. 실패하면 MailException */
    @Override
    public void sendNow(EmailMessage message) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(mailProperties.from());
        mail.setTo(message.to());
        mail.setSubject(message.subject());
        mail.setText(message.body());
        mailSender.send(mail);
        log.info("Mail sent: subject='{}'", message.subject());
    }
}
