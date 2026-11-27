package com.siot.backend.schedule.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 공통 세금 일정을 미리 만들어 둔다: 서버가 뜰 때와 매일 새벽. 올해는 항상, 내년은 12월부터
 * (천문연이 다음 해 공휴일을 발표한 뒤에 만들도록. 그 전에 필요하면 관리자가 연도를 지정해 만든다).
 * 이미 있는 일정은 건드리지 않으므로 여러 번 돌아도 같다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "siot.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TaxScheduleJob {

    /** 이 달부터 내년 일정을 만든다 */
    static final int NEXT_YEAR_FROM_MONTH = 12;

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final TaxScheduleGenerator generator;
    private final Clock clock;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        run();
    }

    @Scheduled(cron = "${siot.scheduling.tax-schedule-cron:0 30 4 * * *}", zone = "Asia/Seoul")
    public void run() {
        LocalDate today = LocalDate.now(clock.withZone(SEOUL));
        generateQuietly(today.getYear());
        if (today.getMonthValue() >= NEXT_YEAR_FROM_MONTH) {
            generateQuietly(today.getYear() + 1);
        }
    }

    private void generateQuietly(int year) {
        try {
            TaxScheduleGenerator.Result result = generator.generate(year);
            if (!result.created().isEmpty() && !result.officialHolidays()) {
                log.warn("Tax schedules for {} were created without official holiday data. "
                        + "Check the deadlines in the admin schedule list (lunar and substitute holidays are not included).", year);
            }
        } catch (RuntimeException e) {
            // 서버 시작을 막지 않는다. 다음 주기에 다시 시도
            log.error("Tax schedule generation for {} failed", year, e);
        }
    }
}
