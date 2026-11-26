package com.siot.backend.event.collect;

import com.siot.backend.event.domain.EventCollectionRun;
import com.siot.backend.event.repository.EventCollectionRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

/** event_collection_run 기록. 저장이 실패해 되돌려져도 실행 기록은 남도록 따로 커밋한다 */
@Service
@RequiredArgsConstructor
public class EventCollectionRunRecorder {

    private final EventCollectionRunRepository runRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long start(EventSourceType source) {
        return runRepository.save(EventCollectionRun.start(source.name(), OffsetDateTime.now(clock))).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void succeed(Long runId, int fetched, int upserted) {
        runRepository.findById(runId).ifPresent(run -> run.succeed(fetched, upserted, OffsetDateTime.now(clock)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long runId, int fetched, String error) {
        runRepository.findById(runId).ifPresent(run -> run.fail(fetched, error, OffsetDateTime.now(clock)));
    }
}
