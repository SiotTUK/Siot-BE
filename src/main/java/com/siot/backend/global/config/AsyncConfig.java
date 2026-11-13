package com.siot.backend.global.config;

import com.siot.backend.global.mail.MailProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 비동기 작업(메일 발송 등) 활성화. 스레드 풀은 Spring Boot 기본 applicationTaskExecutor 를 사용한다.
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties({MailProperties.class, FrontendProperties.class})
public class AsyncConfig {
}
