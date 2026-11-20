package com.siot.backend.rag.service;

import com.siot.backend.global.exception.BusinessException;
import com.siot.backend.global.exception.ErrorCode;
import com.siot.backend.rag.document.FileStorage;
import com.siot.backend.rag.domain.ChangeType;
import com.siot.backend.rag.domain.Document;
import com.siot.backend.rag.domain.DocumentVersion;
import com.siot.backend.rag.domain.ProcessingStatus;
import com.siot.backend.rag.dto.DocumentCreateRequest;
import com.siot.backend.rag.dto.DocumentResponse;
import com.siot.backend.rag.dto.DocumentUpdateRequest;
import com.siot.backend.rag.dto.DocumentVersionCreateRequest;
import com.siot.backend.rag.dto.DocumentVersionResponse;
import com.siot.backend.rag.repository.DocumentRepository;
import com.siot.backend.rag.repository.DocumentVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 관리자 문서 관리 (ADM-02 등록·교체, ADM-03 정보 관리, ADM-04 재반영).
 * 업로드하면 원본을 저장하고 PENDING 버전을 만든 뒤, 커밋되면 {@link DocumentProcessingQueue} 가 반영한다.
 * 예전 버전·조각은 지우지 않는다 (CB-17).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentAdminService {

    /** PDF 머리말("%PDF-")은 파일 앞 1024바이트 안에 있어야 한다 (PDF 규격) */
    private static final int PDF_HEADER_WINDOW = 1024;
    private static final byte[] PDF_HEADER = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final List<ProcessingStatus> IN_PROGRESS = List.of(ProcessingStatus.PENDING, ProcessingStatus.PROCESSING);

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final FileStorage fileStorage;
    private final ApplicationEventPublisher eventPublisher;

    /** ADM-03: 문서 목록 (최근 등록 순) */
    @Transactional(readOnly = true)
    public Page<DocumentResponse> list(Pageable pageable) {
        Page<Document> documents = documentRepository.findAll(pageable);
        List<Long> ids = documents.map(Document::getId).toList();
        Map<Long, List<DocumentVersion>> versions = ids.isEmpty() ? Map.of()
                : versionRepository.findByDocumentIdInOrderByVersionNoDesc(ids).stream()
                .collect(Collectors.groupingBy(v -> v.getDocument().getId(), LinkedHashMap::new, Collectors.toList()));
        return documents.map(document -> toResponse(document, versions.getOrDefault(document.getId(), List.of())));
    }

    /** ADM-02: 새 문서와 첫 버전 */
    @Transactional
    public DocumentResponse create(Long adminId, DocumentCreateRequest request) {
        byte[] content = readPdf(request.file());
        Document document = documentRepository.save(
                Document.create(request.name().strip(), request.sourceUrl().strip(), request.publishedOn()));
        DocumentVersion version = storeVersion(document, 1, request.file(), content, checksum(content),
                adminId, ChangeType.NEW, null);
        return DocumentResponse.from(document, null, version);
    }

    /** ADM-03: 문서명·원문 링크·발행일·사용 여부 */
    @Transactional
    public DocumentResponse update(Long documentId, DocumentUpdateRequest request) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
        document.update(strip(request.name()), strip(request.sourceUrl()), request.publishedOn(), request.active());
        documentRepository.flush();
        return toResponse(document, versionRepository.findByDocumentIdOrderByVersionNoDesc(documentId));
    }

    /** ADM-02: 기존 문서의 새 버전 */
    @Transactional
    public DocumentVersionResponse addVersion(Long adminId, Long documentId, DocumentVersionCreateRequest request) {
        byte[] content = readPdf(request.file());
        Document document = documentRepository.findByIdForUpdate(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
        if (versionRepository.existsByDocumentIdAndProcessingStatusIn(documentId, IN_PROGRESS)) {
            throw new BusinessException(ErrorCode.DOCUMENT_VERSION_IN_PROGRESS);
        }
        String checksum = checksum(content);
        boolean unchanged = versionRepository.findByDocumentIdAndCurrentTrue(documentId)
                .map(current -> checksum.equals(current.getChecksum()))
                .orElse(false);
        if (unchanged) {
            throw new BusinessException(ErrorCode.DOCUMENT_FILE_UNCHANGED);
        }
        int versionNo = versionRepository.findMaxVersionNo(documentId) + 1;
        DocumentVersion version = storeVersion(document, versionNo, request.file(), content, checksum,
                adminId, ChangeType.REPLACE, strip(request.changeSummary()));
        return DocumentVersionResponse.from(version, 0);
    }

    /** ADM-04·NTF-06: 버전 목록 (최신 먼저) */
    @Transactional(readOnly = true)
    public List<DocumentVersionResponse> versions(Long documentId) {
        if (!documentRepository.existsById(documentId)) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        List<DocumentVersion> versions = versionRepository.findByDocumentIdOrderByVersionNoDesc(documentId);
        Map<Long, Long> chunkCounts = chunkCounts(versions.stream().map(DocumentVersion::getId).toList());
        return versions.stream()
                .map(version -> DocumentVersionResponse.from(version, chunkCounts.getOrDefault(version.getId(), 0L)))
                .toList();
    }

    /** ADM-04: 실패한 버전 다시 반영. 현재 버전보다 오래된 버전은 안 된다 (옛 내용이 최신을 밀어내지 않게) */
    @Transactional
    public DocumentVersionResponse retry(Long versionId) {
        DocumentVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_VERSION_NOT_FOUND));
        Long documentId = version.getDocument().getId();
        documentRepository.findByIdForUpdate(documentId);
        if (version.getProcessingStatus() != ProcessingStatus.FAILED) {
            throw new BusinessException(ErrorCode.DOCUMENT_VERSION_NOT_RETRYABLE);
        }
        boolean superseded = versionRepository.findByDocumentIdAndCurrentTrue(documentId)
                .map(current -> current.getVersionNo() > version.getVersionNo())
                .orElse(false);
        if (superseded) {
            throw new BusinessException(ErrorCode.DOCUMENT_VERSION_NOT_RETRYABLE);
        }
        if (versionRepository.existsByDocumentIdAndProcessingStatusIn(documentId, IN_PROGRESS)) {
            throw new BusinessException(ErrorCode.DOCUMENT_VERSION_IN_PROGRESS);
        }
        version.requeue();
        eventPublisher.publishEvent(new DocumentProcessingQueue.VersionQueued(version.getId()));
        return DocumentVersionResponse.from(version, 0);
    }

    private DocumentVersion storeVersion(Document document, int versionNo, MultipartFile file, byte[] content,
                                         String checksum, Long adminId, ChangeType changeType, String changeSummary) {
        String key = "documents/" + document.getId() + "/v" + versionNo + "-" + UUID.randomUUID() + ".pdf";
        fileStorage.save(key, content);
        // DB 기록이 실패하면 저장한 파일을 지운다 (성공하면 계속 보관)
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try {
                        fileStorage.delete(key);
                    } catch (RuntimeException e) {
                        log.warn("Failed to delete orphan document file {}", key, e);
                    }
                }
            }
        });
        DocumentVersion version = versionRepository.save(DocumentVersion.create(document, versionNo,
                originalFilename(file), key, checksum, adminId, changeType, changeSummary));
        eventPublisher.publishEvent(new DocumentProcessingQueue.VersionQueued(version.getId()));
        return version;
    }

    private DocumentResponse toResponse(Document document, List<DocumentVersion> versionsNewestFirst) {
        DocumentVersion current = versionsNewestFirst.stream().filter(DocumentVersion::isCurrent).findFirst().orElse(null);
        DocumentVersion latest = versionsNewestFirst.isEmpty() ? null : versionsNewestFirst.getFirst();
        return DocumentResponse.from(document, current, latest);
    }

    private Map<Long, Long> chunkCounts(Collection<Long> versionIds) {
        if (versionIds.isEmpty()) {
            return Map.of();
        }
        return versionRepository.countChunks(versionIds).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }

    /** 비어 있지 않고 PDF 머리말이 있는 파일만 */
    private static byte[] readPdf(MultipartFile file) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.DOCUMENT_FILE_INVALID);
        }
        if (!hasPdfHeader(content)) {
            throw new BusinessException(ErrorCode.DOCUMENT_FILE_INVALID);
        }
        return content;
    }

    static boolean hasPdfHeader(byte[] content) {
        int limit = Math.min(content.length - PDF_HEADER.length, PDF_HEADER_WINDOW);
        for (int start = 0; start <= limit; start++) {
            boolean match = true;
            for (int i = 0; i < PDF_HEADER.length; i++) {
                if (content[start + i] != PDF_HEADER[i]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return true;
            }
        }
        return false;
    }

    private static String checksum(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 경로를 뺀 파일 이름 (255자 이하) */
    private static String originalFilename(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            return null;
        }
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1).strip();
        return name.length() <= 255 ? name : name.substring(name.length() - 255);
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }
}
