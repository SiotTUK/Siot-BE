package com.siot.backend.event.collect;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** EVE-01: 매일 새벽 3시 (siot.scheduling.event-collect-cron, 한국 시간) 행사 수집 */
@Component
@RequiredArgsConstructor
public class EventCollectionScheduler {

    private final EventCollectionService collectionService;

    @Scheduled(cron = "${siot.scheduling.event-collect-cron}", zone = "${siot.scheduling.zone}")
    public void collect() {
        collectionService.collectAll();
    }
}
