package com.siot.backend.global.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 예약 작업 설정 (application.yml 의 siot.scheduling.*).
 *
 * @param enabled                      예약 작업 전체 사용 여부
 * @param zone                         cron 기준 시간대
 * @param eventCollectCron             EVE-01 행사 수집 주기 (기본: 매일 새벽 3시)
 * @param notificationDispatchInterval NTF-05 예약 알림 발송 확인 주기
 */
@Validated
@ConfigurationProperties(prefix = "siot.scheduling")
public record SchedulingProperties(
        boolean enabled,
        @NotBlank String zone,
        @NotBlank String eventCollectCron,
        @NotNull Duration notificationDispatchInterval
) {
}
