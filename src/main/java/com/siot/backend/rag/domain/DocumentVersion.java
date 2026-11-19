package com.siot.backend.rag.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "document_version")
public class DocumentVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "document_version_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "file_storage_key", nullable = false, columnDefinition = "text")
    private String fileStorageKey;

    private String checksum;

    @Column(name = "uploaded_by")
    private Long uploadedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", length = 30)
    private ChangeType changeType;

    @Column(name = "change_summary", columnDefinition = "text")
    private String changeSummary;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 30)
    private ProcessingStatus processingStatus = ProcessingStatus.PENDING;

    @Column(name = "processing_error", columnDefinition = "text")
    private String processingError;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    @Column(name = "is_current", nullable = false)
    private boolean current = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** ADM-02: 업로드한 새 버전 (PENDING, 현재 버전 아님) */
    public static DocumentVersion create(Document document, int versionNo, String originalFilename,
                                         String fileStorageKey, String checksum, Long uploadedBy,
                                         ChangeType changeType, String changeSummary) {
        DocumentVersion version = new DocumentVersion();
        version.document = document;
        version.versionNo = versionNo;
        version.originalFilename = originalFilename;
        version.fileStorageKey = fileStorageKey;
        version.checksum = checksum;
        version.uploadedBy = uploadedBy;
        version.changeType = changeType;
        version.changeSummary = changeSummary;
        return version;
    }

    public void startProcessing() {
        this.processingStatus = ProcessingStatus.PROCESSING;
        this.processingError = null;
        this.processedAt = null;
    }

    /** ADM-04: 모든 단계 성공. 이전 현재 버전은 먼저 {@link #release()} 해야 한다 (부분 unique 인덱스) */
    public void complete(OffsetDateTime now) {
        this.processingStatus = ProcessingStatus.COMPLETED;
        this.processingError = null;
        this.processedAt = now;
        this.current = true;
    }

    /** ADM-04·NTF-06: 실패 단계·이유와 끝난 시각. 현재 버전 여부는 바꾸지 않는다 */
    public void fail(ProcessingStage stage, String reason, OffsetDateTime now) {
        this.processingStatus = ProcessingStatus.FAILED;
        this.processingError = stage.tag() + " " + reason;
        this.processedAt = now;
    }

    /** 실패 버전 재실행 대기 */
    public void requeue() {
        this.processingStatus = ProcessingStatus.PENDING;
        this.processingError = null;
        this.processedAt = null;
    }

    /** 새 버전이 반영되어 검색에서 빠진다. 버전·조각은 지우지 않는다 (CB-17) */
    public void release() {
        this.current = false;
    }
}
