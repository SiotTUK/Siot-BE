package com.siot.backend.rag.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

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

    @Column(name = "change_type", length = 30)
    private String changeType;

    @Column(name = "change_summary", columnDefinition = "text")
    private String changeSummary;

    @Column(name = "processing_status", nullable = false, length = 30)
    private String processingStatus = "PENDING";

    @Column(name = "processing_error", columnDefinition = "text")
    private String processingError;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "is_current", nullable = false)
    private boolean current = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
