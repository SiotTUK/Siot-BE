package com.siot.backend.global.mail;

/**
 * 메일 발송. 구현체는 비동기로 보내며, 실패는 로그로 남기고 호출자에게 예외를 던지지 않는다.
 * <p>
 * 비동기인 이유: 비밀번호 재설정(M-11)은 계정·이메일 존재 여부와 관계없이 같은 응답을 해야 하는데,
 * 메일을 보낼 때만 응답이 느려지면 존재 여부가 드러나기 때문이다.
 */
public interface EmailSender {

    void send(EmailMessage message);
}
