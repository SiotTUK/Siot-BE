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

        @Schema(description = "안내 항목 ID (생활수칙·체크리스트·생활 변화 할 일)", example = "1")
        Long guideId,
        @Schema(description = "신고 대상 종류: EVENT(행사) / RULE(안내 항목: 생활수칙·체크리스트·생활 변화 할 일, guideId 로 구분). 대상을 지웠으면 null (제목은 targetTitleSnapshot)",
                nullable = true, example = "RULE")
        String targetType,

        @Schema(description = "신고 대상 제목", example = "외국인등록")
        String targetTitleSnapshot,

        @Schema(description = "인용 문구", example = "수수료 30,000원")
        String quotedText,

        @Schema(description = "신고 당시 화면 언어", example = "ko")
        String language,

        @Schema(description = "신고 사유: TRANSLATION(번역 오류) / CONTENT(내용 오류) / OTHER(기타)", example = "CONTENT")
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
        OffsetDateTime updatedAt,

        @Schema(description = "#14 대상 코드: 행사는 eventId, 안내 항목은 code (waste-food, CL-01, LC-01-1). 대상을 지웠으면 null",
                nullable = true, example = "waste-food")
        String targetCode,

        @Schema(description = "#14 대상 종류: EVENT / RULE(생활수칙) / CHECKLIST / LIFE_CHANGE_TASK. 관리자 화면에서 대상 화면으로 갈 때 쓴다",
                nullable = true, example = "RULE")
        String targetKind
) {
    /** 대상 항목을 모를 때 (안내 항목 코드·종류 없음) */
    public static ContentReportResponse from(ContentReport r) {
        return from(r, null);
    }

    /** @param guide 신고 대상 안내 항목 (행사 신고거나 지웠으면 null) */
    public static ContentReportResponse from(ContentReport r, com.siot.backend.checklist.domain.Guide guide) {
        String targetCode = r.getEventId() != null ? r.getEventId().toString() : guide != null ? guide.getCode() : null;
        String targetKind = r.getEventId() != null ? "EVENT" : guide != null ? guide.getKind() : null;
        return new ContentReportResponse(
                r.getId(),
                r.getReporterUserId(),
                r.getEventId(),
                r.getGuideId(),
                r.getEventId() != null ? "EVENT" : r.getGuideId() != null ? "RULE" : null,
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
                r.getUpdatedAt(),
                targetCode,
                targetKind
        );
    }
}
