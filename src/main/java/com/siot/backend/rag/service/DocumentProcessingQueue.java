package com.siot.backend.rag.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * ADM-04: 문서 반영 작업을 한 번에 하나씩 처리한다.
 * <ul>
 *     <li>업로드·재실행 트랜잭션이 커밋된 뒤에 넣는다 (커밋 전에 처리하면 버전 행이 안 보인다)</li>
 *     <li>스레드 1개: 무료 등급 한도를 지키고, 같은 문서의 버전 전환이 엇갈리지 않게 한다</li>
 *     <li>서버가 다시 시작되면 대기·처리 중이던 버전을 이어서 처리한다</li>
 * </ul>
 * 단일 서버 배포 기준이다 (SchedulingConfig 와 같음).
 */
@Slf4j
@Component
public class DocumentProcessingQueue {

    /** 업로드·재실행으로 처리할 버전이 생김 */
    public record VersionQueued(Long versionId) {
    }

    private final DocumentProcessor processor;
    private final DocumentVersionStateService stateService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "document-ingest");
        thread.setDaemon(true);
        return thread;
    });

    public DocumentProcessingQueue(DocumentProcessor processor, DocumentVersionStateService stateService) {
        this.processor = processor;
        this.stateService = stateService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onQueued(VersionQueued event) {
        submit(event.versionId());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void resume() {
        List<Long> versionIds = stateService.resumable();
        if (!versionIds.isEmpty()) {
            log.info("Resuming {} document versions: {}", versionIds.size(), versionIds);
        }
        versionIds.forEach(this::submit);
    }

    private void submit(Long versionId) {
        executor.execute(() -> {
            try {
                processor.process(versionId);
            } catch (RuntimeException e) {
                log.error("Document version {} processing crashed", versionId, e);
            }
        });
    }

    @PreDestroy
    void shutdown() throws InterruptedException {
        executor.shutdownNow();
        executor.awaitTermination(10, TimeUnit.SECONDS);
    }
}
