package com.siot.backend.report.dto;

import com.siot.backend.report.domain.ContentReport;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "콘텐츠 오류 신고 응답")
public record ContentReportResponse(
        @Schema(description = "신고 ID", example = "1")
        Long reportId,

        @Schema(description = "신고자 회원 ID", example = "10")
        Long reporterUserId,

        @Schema(description = "행사 ID", example = "null")
        Long eventId,

        @Schema(description = "가이드 ID", example = "1")
        Long guideId,

        @Schema(description = "신고 대상 제목", example = "외국인등록")
        String targetTitleSnapshot,

        @Schema(description = "인용 문구", example = "수수료 30,000원")
        String quotedText,

        @Schema(description = "신고 당시 화면 언어", example = "ko")
        String language,

        @Schema(description = "신고 사유", example = "정보 오류")
        String reason,

        @Schema(description = "상세 설명")
        String description,

        @Schema(description = "처리 상태 (RECEIVED, IN_REVIEW, RESOLVED, REJECTED)", example = "RECEIVED")
        String status,

        @Schema(description = "관리자 처리 메모")
        String adminMemo,

        @Schema(description = "처리한 관리자 ID")
        Long processedBy,

        @Schema(description = "처리 완료 시각")
        OffsetDateTime processedAt,

        @Schema(description = "신고 접수 시각")
        OffsetDateTime createdAt,

        @Schema(description = "최종 수정 시각")
        OffsetDateTime updatedAt
) {
    public static ContentReportResponse from(ContentReport r) {
        return new ContentReportResponse(
                r.getId(),
                r.getReporterUserId(),
                r.getEventId(),
                r.getGuideId(),
                r.getTargetTitleSnapshot(),
                r.getQuotedText(),
                r.getLanguage(),
                r.getReason(),
                r.getDescription(),
                r.getStatus(),
                r.getAdminMemo(),
                r.getProcessedBy(),
                r.getProcessedAt(),
                r.getCreatedAt(),
                r.getUpdatedAt()
        );
    }
}
