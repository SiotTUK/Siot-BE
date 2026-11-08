package com.siot.backend.global.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 예약 작업(아키텍처 "예약 작업") 활성화.
 * 실제 작업은 각 기능 패키지에서 @Scheduled 로 구현한다. 예:
 *
 * <pre>
 * &#64;Scheduled(cron = "${siot.scheduling.event-collect-cron}", zone = "${siot.scheduling.zone}")
 * void collectEvents() { ... }           // event 패키지 (EVE-01)
 *
 * &#64;Scheduled(fixedDelayString = "${siot.scheduling.notification-dispatch-interval}")
 * void dispatchNotifications() { ... }   // notification 패키지 (NTF-05)
 * </pre>
 *
 * 현재는 단일 서버 배포 기준이다. 백엔드 인스턴스를 여러 대로 늘리면 작업 중복 실행 방지(분산 락)가 필요하다.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(SchedulingProperties.class)
@ConditionalOnProperty(prefix = "siot.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
