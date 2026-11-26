package com.siot.backend.global.mail;

/**
 * 메일 발송. {@link #send} 는 비동기로 보내며, 실패는 로그로 남기고 호출자에게 예외를 던지지 않는다.
 * <p>
 * 비동기인 이유: 비밀번호 재설정(M-11)은 계정·이메일 존재 여부와 관계없이 같은 응답을 해야 하는데,
 * 메일을 보낼 때만 응답이 느려지면 존재 여부가 드러나기 때문이다.
 */
public interface EmailSender {

    void send(EmailMessage message);

    /**
     * 지금 보내고 실패하면 예외를 던진다. 발송 결과를 기록해야 하는 곳(NTF-05 알림 메일 발송기)에서 쓴다.
     * {@link #send} 는 비동기라 바로 돌아오고 실패를 삼키므로, 그 결과로는 성공·실패를 알 수 없다.
     * 기본 구현은 {@link #send} 를 부른다 (테스트 대역용). SMTP 구현은 동기로 보낸다.
     */
    default void sendNow(EmailMessage message) {
        send(message);
    }
}
