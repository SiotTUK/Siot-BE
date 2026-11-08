package com.siot.backend.notification.domain;

/**
 * 알림 이메일 발송 상태 (NTF-05).
 * 이메일 발송 대상이 아닌 알림은 상태 값이 null 이다.
 */
public enum EmailDeliveryStatus {
    PENDING,
    SENT,
    FAILED
}
