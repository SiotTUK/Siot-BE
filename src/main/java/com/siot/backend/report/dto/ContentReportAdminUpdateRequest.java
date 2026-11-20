package com.siot.backend.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "관리자 콘텐츠 오류 신고 처리 요청 (ADM-07)")
public record ContentReportAdminUpdateRequest(
        @NotBlank
        @Schema(description = "변경할 상태 (RECEIVED, IN_REVIEW, RESOLVED, REJECTED)", example = "RESOLVED")
        String status,

        @Schema(description = "관리자 처리 메모", example = "수수료 최신 정보로 수정 반영 완료")
        String adminMemo
) {
}
