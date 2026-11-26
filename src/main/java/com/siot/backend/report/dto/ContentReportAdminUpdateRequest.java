package com.siot.backend.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "관리자 콘텐츠 오류 신고 처리 요청 (ADM-07)")
public record ContentReportAdminUpdateRequest(
        @NotBlank
        @Pattern(regexp = "(?i)" + STATUS_PATTERN)
        @Schema(description = "상태: RECEIVED(접수) / IN_REVIEW(확인 중) / RESOLVED(처리 완료) / REJECTED(처리 완료: 반영 안 함)",
                allowableValues = {"RECEIVED", "IN_REVIEW", "RESOLVED", "REJECTED"}, example = "RESOLVED")
        String status,

        @Size(max = 2000)
        @Schema(description = "관리자 처리 메모", nullable = true, example = "수수료 최신 정보로 수정 반영 완료")
        String adminMemo
) {
    public static final String STATUS_PATTERN = "RECEIVED|IN_REVIEW|RESOLVED|REJECTED";
}
