package com.siot.backend.report.controller;

import com.siot.backend.auth.security.MemberPrincipal;
import com.siot.backend.global.common.ApiResponse;
import com.siot.backend.global.common.PageResponse;
import com.siot.backend.report.dto.ContentReportAdminUpdateRequest;
import com.siot.backend.report.dto.ContentReportResponse;
import com.siot.backend.report.service.ContentReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "관리자 - 오류 신고", description = "콘텐츠 오류 신고 목록 조회 및 상태 처리 (ADM-07)")
@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {

    private final ContentReportService contentReportService;

    @Operation(summary = "관리자 오류 신고 목록 조회 (ADM-07)",
            description = "status: RECEIVED, IN_REVIEW, RESOLVED, REJECTED (미입력 시 전체)")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ContentReportResponse>>> getReports(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.of(contentReportService.getReports(status, pageable)));
    }

    @Operation(summary = "관리자 오류 신고 단건 조회")
    @GetMapping("/{reportId}")
    public ResponseEntity<ApiResponse<ContentReportResponse>> getReport(@PathVariable Long reportId) {
        return ResponseEntity.ok(ApiResponse.of(contentReportService.getReport(reportId)));
    }

    @Operation(summary = "관리자 오류 신고 처리 (ADM-07)",
            description = "상태 변경(IN_REVIEW, RESOLVED, REJECTED) 및 관리자 메모를 기록합니다.")
    @PatchMapping("/{reportId}")
    public ResponseEntity<ApiResponse<ContentReportResponse>> processReport(
            @PathVariable Long reportId,
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody ContentReportAdminUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.of(contentReportService.processReport(reportId, request, principal.getId())));
    }
}
