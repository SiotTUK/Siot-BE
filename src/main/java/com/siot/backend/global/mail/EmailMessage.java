package com.siot.backend.global.mail;

/** 일반 텍스트 메일 한 통 */
public record EmailMessage(String to, String subject, String body) {
}
