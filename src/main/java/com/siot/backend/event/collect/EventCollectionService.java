package com.siot.backend.event.collect;

import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 행사 수집 한 주기 (EVE-01, EVE-02). 출처마다 따로 실행하고 따로 기록한다: 한 출처가 실패해도 나머지는 진행.
 * 출처 순서는 {@link EventSourceType} 선언 순서 (먼저 저장된 행사가 출처 간 중복의 기준).
 * 수집이 끝나면 번역이 필요한 행사를 번역한다.
 */
@Slf4j
public class EventCollectionService {

    private final List<EventSource> sources;
    private final EventUpsertService upsertService;
    private final EventCollectionRunRecorder recorder;
    private final EventChangeNotifier changeNotifier;
    private final EventTranslationUpdater translationUpdater;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public EventCollectionService(List<EventSource> sources, EventUpsertService upsertService,
                                  EventCollectionRunRecorder recorder, EventChangeNotifier changeNotifier,
                                  EventTranslationUpdater translationUpdater, Clock clock) {
        this.sources = sources.stream().sorted(Comparator.comparing(EventSource::type)).toList();
        this.upsertService = upsertService;
        this.recorder = recorder;
        this.changeNotifier = changeNotifier;
        this.translationUpdater = translationUpdater;
        this.clock = clock;
    }

    /** 이미 수집 중이면 건너뛴다 */
    public void collectAll() {
        if (!running.compareAndSet(false, true)) {
            log.warn("Event collection is already running, skipped");
            return;
        }
        try {
            LocalDate today = LocalDate.now(clock);
            for (EventSource source : sources) {
                collect(source, today);
            }
            int translated = translationUpdater.translatePending(today);
            log.info("Event translation: {} events translated", translated);
        } finally {
            running.set(false);
        }
    }

    /** 출처 하나. 실패하면 기존 행사는 그대로 두고 FAILED 로 남긴다 */
    void collect(EventSource source, LocalDate today) {
        Long runId = recorder.start(source.type());
        int fetched = 0;
        try {
            FetchResult result = source.fetch(today);
            fetched = result.fetchedCount();
            EventUpsertService.Result saved = upsertService.upsert(source.type(), result.events());
            recorder.succeed(runId, fetched, saved.upserted());
            log.info("Event collection {}: fetched {}, current {}, upserted {}", source.type(), fetched,
                    result.events().size(), saved.upserted());
            saved.changes().forEach(this::notifyChange);
        } catch (RuntimeException e) {
            log.warn("Event collection {} failed, existing events kept: {}", source.type(), e.getMessage(), e);
            recorder.fail(runId, fetched, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void notifyChange(EventChangeNotifier.ScheduleChange change) {
        try {
            changeNotifier.scheduleChanged(change);
        } catch (RuntimeException e) {
            log.warn("Event schedule change notification failed for event {}: {}", change.eventId(), e.getMessage());
        }
    }
}
