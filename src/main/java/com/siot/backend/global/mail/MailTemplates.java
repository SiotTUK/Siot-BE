package com.siot.backend.global.mail;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 사용자 언어(ko, en, zh, vi)로 메일 제목·본문을 만든다. 문구: resources/messages/mail*.properties
 */
@Component
@RequiredArgsConstructor
public class MailTemplates {

    private final MessageSource messageSource;

    public EmailMessage emailVerification(String to, String language, String code, long validMinutes) {
        return build(to, language, "mail.email-verification", code, validMinutes);
    }

    /** 받는 주소가 곧 계정(아이디)이다 */
    public EmailMessage passwordReset(String to, String language, String link, long validMinutes) {
        return build(to, language, "mail.password-reset", to, link, validMinutes);
    }

    private EmailMessage build(String to, String language, String key, Object... args) {
        Locale locale = Locale.forLanguageTag(language == null ? "ko" : language);
        String subject = messageSource.getMessage(key + ".subject", null, locale);
        String body = messageSource.getMessage(key + ".body", args, locale);
        return new EmailMessage(to, subject, body);
    }
}
