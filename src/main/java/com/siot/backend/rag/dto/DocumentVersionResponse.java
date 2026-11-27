package com.siot.backend.rag.dto;

import com.siot.backend.rag.domain.ChangeType;
import com.siot.backend.rag.domain.DocumentVersion;
import com.siot.backend.rag.domain.ProcessingStage;
import com.siot.backend.rag.domain.ProcessingStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/** ADM-04·NTF-06: 버전과 반영 결과. 파일 업로드 완료(uploadedAt)와 검색 반영 완료(processedAt)를 구분한다 */
@Schema(description = "ADM-04 문서 버전·반영 상태")
public record DocumentVersionResponse(
        @Schema(description = "버전 ID", example = "12") Long versionId,
        @Schema(description = "문서 ID", example = "3") Long documentId,
        @Schema(description = "버전 번호 (문서마다 1부터)", example = "2") int versionNo,
        @Schema(description = "올린 파일 이름", nullable = true, example = "guide-2026.pdf") String originalFilename,
        @Schema(description = "NEW: 새 문서 등록, REPLACE: 기존 문서 교체, REPROCESS: 같은 파일을 지금 조각 규칙으로 재처리", nullable = true, example = "REPLACE") ChangeType changeType,
        @Schema(description = "바뀐 내용 요약", nullable = true) String changeSummary,
        @Schema(description = "이 버전에서 적재에서 뺀 쪽. 없으면 null", nullable = true, example = "51-52,100") String excludedPages,
        @Schema(description = "PENDING(대기) / PROCESSING(처리 중) / COMPLETED(완료) / FAILED(실패)", example = "COMPLETED")
        ProcessingStatus status,
        @Schema(description = "챗봇 검색에 쓰는 현재 버전이면 true", example = "true") boolean current,
        @Schema(description = "실패한 단계 EXTRACT(텍스트 추출) / SPLIT(분할) / EMBEDDING(임베딩) / INDEX(검색 반영). 실패가 아니면 null",
                nullable = true, example = "EMBEDDING") ProcessingStage failedStage,
        @Schema(description = "실패 이유", nullable = true, example = "Gemini 임베딩 실패: ...") String failureReason,
        @Schema(description = "저장한 조각 수 (완료된 버전만)", example = "184") long chunkCount,
        @Schema(description = "올린 관리자 ID", nullable = true, example = "1") Long uploadedBy,
        @Schema(description = "파일 업로드 완료 시각") OffsetDateTime uploadedAt,
        @Schema(description = "반영이 끝난 시각 (성공·실패). 대기·처리 중이면 null", nullable = true) OffsetDateTime processedAt) {

    public static DocumentVersionResponse from(DocumentVersion version, long chunkCount) {
        boolean failed = version.getProcessingStatus() == ProcessingStatus.FAILED;
        return new DocumentVersionResponse(
                version.getId(),
                version.getDocument().getId(),
                version.getVersionNo(),
                version.getOriginalFilename(),
                version.getChangeType(),
                version.getChangeSummary(),
                version.getExcludedPages(),
                version.getProcessingStatus(),
                version.isCurrent(),
                failed ? ProcessingStage.fromError(version.getProcessingError()) : null,
                failed ? ProcessingStage.reasonOf(version.getProcessingError()) : null,
                chunkCount,
                version.getUploadedBy(),
                version.getCreatedAt(),
                version.getProcessedAt());
    }
}
