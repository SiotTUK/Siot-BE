package com.siot.backend.event.collect;

import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 행사 수집 한 주기 (EVE-01, EVE-02). 출처마다 따로 실행하고 따로 기록한다: 한 출처가 실패해도 나머지는 진행.
 * 출처 순서는 {@link EventSourceType} 선언 순서 (먼저 저장된 행사가 출처 간 중복의 기준).
 * 수집이 끝나면 번역이 필요한 행사를 번역한다.
 * <p>
 * 한 번에 한 주기만 돈다. 스케줄({@link #collectAll})과 관리자 수동 실행({@link #collectNow}, ADM-05)이 같은 코드를 쓰고,
 * 번역이 끝날 때까지 "수집 중"이다.
 */
@Slf4j
public class EventCollectionService {

    /** 이미 수집(또는 그 뒤 번역)이 돌고 있다 */
    public static class AlreadyRunningException extends RuntimeException {
        AlreadyRunningException() {
            super("Event collection is already running");
        }
    }

    private final List<EventSource> sources;
    private final EventUpsertService upsertService;
    private final EventCollectionRunRecorder recorder;
    private final EventChangeNotifier changeNotifier;
    private final EventTranslationUpdater translationUpdater;
    private final Clock clock;
    /** 수동 실행 뒤 번역을 돌릴 곳 */
    private final Executor translationExecutor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public EventCollectionService(List<EventSource> sources, EventUpsertService upsertService,
                                  EventCollectionRunRecorder recorder, EventChangeNotifier changeNotifier,
                                  EventTranslationUpdater translationUpdater, Clock clock) {
        this(sources, upsertService, recorder, changeNotifier, translationUpdater, clock,
                Executors.newSingleThreadExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "event-translation");
                    thread.setDaemon(true);
                    return thread;
                }));
    }

    public EventCollectionService(List<EventSource> sources, EventUpsertService upsertService,
                                  EventCollectionRunRecorder recorder, EventChangeNotifier changeNotifier,
                                  EventTranslationUpdater translationUpdater, Clock clock,
                                  Executor translationExecutor) {
        this.sources = sources.stream().sorted(Comparator.comparing(EventSource::type)).toList();
        this.upsertService = upsertService;
        this.recorder = recorder;
        this.changeNotifier = changeNotifier;
        this.translationUpdater = translationUpdater;
        this.clock = clock;
        this.translationExecutor = translationExecutor;
    }

    /**
     * 스케줄 실행: 수집하고 같은 스레드에서 번역까지 한다. 이미 돌고 있으면 건너뛴다.
     *
     * @return 실행했으면 true
     */
    public boolean collectAll() {
        if (!running.compareAndSet(false, true)) {
            log.warn("Event collection is already running, skipped");
            return false;
        }
        try {
            LocalDate today = LocalDate.now(clock);
            collectSources(today);
            translate(today);
            return true;
        } finally {
            running.set(false);
        }
    }

    /**
     * 관리자 수동 실행 (ADM-05): 출처 수집이 끝날 때까지 기다렸다가 돌려주고, 번역은 뒤에서 한다.
     *
     * @return 이번 실행의 event_collection_run ID (출처 순서)
     * @throws AlreadyRunningException 이미 수집 또는 번역 중
     */
    public List<Long> collectNow() {
        if (!running.compareAndSet(false, true)) {
            throw new AlreadyRunningException();
        }
        LocalDate today = LocalDate.now(clock);
        List<Long> runIds;
        try {
            runIds = collectSources(today);
        } catch (RuntimeException e) {
            running.set(false);
            throw e;
        }
        try {
            translationExecutor.execute(() -> {
                try {
                    translate(today);
                } finally {
                    running.set(false);
                }
            });
        } catch (RejectedExecutionException e) {
            log.warn("Event translation after manual collection was not started, will run next cycle: {}",
                    e.getMessage());
            running.set(false);
        }
        return runIds;
    }

    /** 수집 또는 그 뒤 번역이 돌고 있으면 true */
    public boolean isRunning() {
        return running.get();
    }

    private List<Long> collectSources(LocalDate today) {
        List<Long> runIds = new ArrayList<>();
        for (EventSource source : sources) {
            runIds.add(collect(source, today));
        }
        return runIds;
    }

    private void translate(LocalDate today) {
        try {
            int translated = translationUpdater.translatePending(today);
            log.info("Event translation: {} events translated", translated);
        } catch (RuntimeException e) {
            log.warn("Event translation failed, will retry next cycle: {}", e.getMessage(), e);
        }
    }

    /** 출처 하나. 실패하면 기존 행사는 그대로 두고 FAILED 로 남긴다 */
    Long collect(EventSource source, LocalDate today) {
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
        return runId;
    }

    private void notifyChange(EventChangeNotifier.ScheduleChange change) {
        try {
            changeNotifier.scheduleChanged(change);
        } catch (RuntimeException e) {
            log.warn("Event schedule change notification failed for event {}: {}", change.eventId(), e.getMessage());
        }
    }
}
