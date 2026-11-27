package com.siot.backend.rag.service;

import com.siot.backend.rag.domain.DocumentVersion;
import com.siot.backend.rag.domain.ProcessingStage;
import com.siot.backend.rag.domain.ProcessingStatus;
import com.siot.backend.rag.repository.DocumentVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

/** ADM-04: 반영 상태 변경. 단계마다 따로 커밋해 관리자가 진행 상태를 바로 볼 수 있게 한다 */
@Service
@RequiredArgsConstructor
public class DocumentVersionStateService {

    /** processing_error 에 남기는 이유의 최대 길이 */
    static final int MAX_REASON_LENGTH = 1000;

    private final DocumentVersionRepository versionRepository;
    private final Clock clock;

    /** 처리할 버전 정보 */
    public record Job(Long versionId, String documentName, String fileStorageKey, String excludedPages) {
    }

    /** PENDING 이면 PROCESSING 으로 바꾸고 작업 정보를 돌려준다. 이미 다른 상태면 null (중복 실행 방지) */
    @Transactional
    public Job start(Long versionId) {
        DocumentVersion version = versionRepository.findById(versionId).orElse(null);
        if (version == null || version.getProcessingStatus() != ProcessingStatus.PENDING) {
            return null;
        }
        version.startProcessing();
        return new Job(version.getId(), version.getDocument().getName(), version.getFileStorageKey(),
                version.getExcludedPages());
    }

    @Transactional
    public void fail(Long versionId, ProcessingStage stage, String reason) {
        versionRepository.findById(versionId).ifPresent(version ->
                version.fail(stage, truncate(reason), OffsetDateTime.now(clock)));
    }

    /** 서버가 다시 시작될 때: 처리 중이던 버전을 대기로 돌리고, 이어서 처리할 버전 ID (오래된 것부터) */
    @Transactional
    public List<Long> resumable() {
        List<DocumentVersion> versions = versionRepository.findByProcessingStatusInOrderByIdAsc(
                List.of(ProcessingStatus.PENDING, ProcessingStatus.PROCESSING));
        versions.stream()
                .filter(version -> version.getProcessingStatus() == ProcessingStatus.PROCESSING)
                .forEach(DocumentVersion::requeue);
        return versions.stream().map(DocumentVersion::getId).toList();
    }

    private static String truncate(String reason) {
        String text = reason == null || reason.isBlank() ? "알 수 없는 오류" : reason.strip();
        return text.length() <= MAX_REASON_LENGTH ? text : text.substring(0, MAX_REASON_LENGTH) + "…";
    }
}
