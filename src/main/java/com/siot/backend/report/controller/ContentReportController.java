package com.siot.backend.report.controller;

import com.siot.backend.auth.security.MemberPrincipal;
import com.siot.backend.global.common.ApiResponse;
import com.siot.backend.report.dto.ContentReportRequest;
import com.siot.backend.report.dto.ContentReportResponse;
import com.siot.backend.report.service.ContentReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "오류 신고", description = "콘텐츠 오류 신고 접수 (COM-01)")
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ContentReportController {

    private final ContentReportService contentReportService;

    @Operation(summary = "콘텐츠 오류 신고 접수 (COM-01)",
            description = "체크리스트, 생활수칙, 행사 등 정보의 오류나 수정 필요 사항을 접수합니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<ContentReportResponse>> createReport(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody ContentReportRequest request
    ) {
        ContentReportResponse response = contentReportService.createReport(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
    }
}
