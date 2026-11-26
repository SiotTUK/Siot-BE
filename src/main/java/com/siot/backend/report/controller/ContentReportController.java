package com.siot.backend.report.controller;

import com.siot.backend.auth.security.MemberPrincipal;
import com.siot.backend.global.common.ApiResponse;
import com.siot.backend.global.config.openapi.ApiErrorCodes;
import com.siot.backend.global.exception.ErrorCode;
import com.siot.backend.global.config.openapi.SuccessStatus;
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

@Tag(name = "8-2. 오류 신고", description = "콘텐츠 오류 신고 접수 (COM-01)")
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ContentReportController {

    private final ContentReportService contentReportService;

    @Operation(summary = "콘텐츠 오류 신고 접수 (COM-01)",
            description = "행사(eventId) 또는 생활수칙(guideId) 중 하나의 잘못된 설명·번역을 신고합니다. "
                    + "문제가 있는 문장(quotedText)과 사유(TRANSLATION 번역 오류 / CONTENT 내용 오류 / OTHER 기타)는 필수이고, "
                    + "화면 언어(language)를 함께 저장합니다. 대상이 없거나 지워졌으면 404, 대상이 둘 다 없거나 둘 다 있으면 400.")
    @SuccessStatus(value = 201, description = "신고 접수 완료")
    @ApiErrorCodes({ErrorCode.INVALID_INPUT, ErrorCode.EVENT_NOT_FOUND, ErrorCode.GUIDE_NOT_FOUND})
    @PostMapping
    public ResponseEntity<ApiResponse<ContentReportResponse>> createReport(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody ContentReportRequest request
    ) {
        ContentReportResponse response = contentReportService.createReport(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
    }
}
