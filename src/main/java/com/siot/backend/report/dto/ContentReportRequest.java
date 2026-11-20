package com.siot.backend.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "콘텐츠 오류 신고 접수 요청 (COM-01)")
public record ContentReportRequest(
        @Schema(description = "가이드(생활수칙/체크리스트) ID", example = "1")
        Long guideId,

        @Schema(description = "행사 ID", example = "null")
        Long eventId,

        @Schema(description = "신고 대상 제목 스냅샷", example = "외국인등록")
        String targetTitleSnapshot,

        @Schema(description = "인용 문구", example = "수수료 30,000원")
        String quotedText,

        @Schema(description = "신고 당시 화면 언어 (기본 ko)", example = "ko")
        String language,

        @NotBlank
        @Size(max = 50)
        @Schema(description = "신고 사유", example = "정보 오류")
        String reason,

        @Schema(description = "상세 설명", example = "수수료가 인상되어 35,000원으로 변경되었습니다.")
        String description
) {
}
