package com.siot.backend.global.mail;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param from 발신자. 예: {@code 웰컴시흥 <team.siot@gmail.com>} (Gmail 은 로그인한 계정 주소만 가능)
 */
@Validated
@ConfigurationProperties(prefix = "siot.mail")
public record MailProperties(@NotBlank String from) {
}
