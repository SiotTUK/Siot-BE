package com.siot.backend.rag.dto;

import com.siot.backend.rag.domain.Document;
import com.siot.backend.rag.domain.DocumentVersion;
import com.siot.backend.rag.domain.ProcessingStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** ADM-03: 문서 정보와 현재 버전·최근 업로드 버전 */
@Schema(description = "ADM-03 문서")
public record DocumentResponse(
        @Schema(description = "문서 ID", example = "3") Long documentId,
        @Schema(description = "문서명", example = "시흥시 외국인주민 생활안내") String name,
        @Schema(description = "공개 원문 링크", nullable = true, example = "https://www.siheung.go.kr/guide.pdf") String sourceUrl,
        @Schema(description = "발행일", nullable = true, example = "2026-03-01") LocalDate publishedOn,
        @Schema(description = "사용 여부. false 면 챗봇 검색에서 빠짐", example = "true") boolean active,
        @Schema(description = "챗봇 검색에 쓰는 현재 버전. 반영이 끝난 버전이 없으면 null", nullable = true) VersionSummary currentVersion,
        @Schema(description = "가장 최근에 올린 버전 (처리 중·실패 확인용). 현재 버전과 같을 수 있음", nullable = true) VersionSummary latestVersion,
        @Schema(description = "등록 시각") OffsetDateTime createdAt,
        @Schema(description = "정보 수정 시각") OffsetDateTime updatedAt) {

    @Schema(description = "버전 요약")
    public record VersionSummary(
            @Schema(description = "버전 ID", example = "12") Long versionId,
            @Schema(description = "버전 번호", example = "2") int versionNo,
            @Schema(description = "PENDING / PROCESSING / COMPLETED / FAILED", example = "COMPLETED") ProcessingStatus status,
            @Schema(description = "반영이 끝난 시각", nullable = true) OffsetDateTime processedAt) {

        static VersionSummary from(DocumentVersion version) {
            return version == null ? null : new VersionSummary(
                    version.getId(), version.getVersionNo(), version.getProcessingStatus(), version.getProcessedAt());
        }
    }

    public static DocumentResponse from(Document document, DocumentVersion current, DocumentVersion latest) {
        return new DocumentResponse(
                document.getId(),
                document.getName(),
                document.getSourceUrl(),
                document.getPublishedOn(),
                document.isActive(),
                VersionSummary.from(current),
                VersionSummary.from(latest),
                document.getCreatedAt(),
                document.getUpdatedAt());
    }
}
