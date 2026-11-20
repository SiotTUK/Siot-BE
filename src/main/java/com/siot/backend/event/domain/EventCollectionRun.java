package com.siot.backend.event.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 행사 수집 실행 기록 (EVE-01). 출처마다 한 행. 최종 갱신 시각(EVE-10) = 출처별 마지막 SUCCEEDED 의 finished_at.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "event_collection_run")
public class EventCollectionRun {

    public static final String RUNNING = "RUNNING";
    public static final String SUCCEEDED = "SUCCEEDED";
    public static final String FAILED = "FAILED";
    /** error 열에 남길 최대 글자 수 */
    private static final int MAX_ERROR = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "run_id")
    private Long id;

    @Column(nullable = false, length = 30)
    private String source;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "fetched_count", nullable = false)
    private int fetchedCount;

    @Column(name = "upserted_count", nullable = false)
    private int upsertedCount;

    @Column(columnDefinition = "text")
    private String error;

    public static EventCollectionRun start(String source, OffsetDateTime startedAt) {
        EventCollectionRun run = new EventCollectionRun();
        run.source = source;
        run.startedAt = startedAt;
        run.status = RUNNING;
        return run;
    }

    public void succeed(int fetched, int upserted, OffsetDateTime finishedAt) {
        this.status = SUCCEEDED;
        this.fetchedCount = fetched;
        this.upsertedCount = upserted;
        this.finishedAt = finishedAt;
    }

    public void fail(int fetched, String error, OffsetDateTime finishedAt) {
        this.status = FAILED;
        this.fetchedCount = fetched;
        this.error = error == null ? null : error.length() > MAX_ERROR ? error.substring(0, MAX_ERROR) : error;
        this.finishedAt = finishedAt;
    }
}
