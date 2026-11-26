package com.siot.backend.notification.domain;

/**
 * 알림 이메일 발송 상태 (NTF-05).
 * 이메일 발송 대상이 아닌 알림은 상태 값이 null 이다.
 */
public enum EmailDeliveryStatus {
    PENDING,
    SENT,
    FAILED,
    /** 회원이 그 알림 종류의 이메일 수신을 꺼서 보내지 않음 (NTF-03 emailEnabled=false) */
    SKIPPED
}
