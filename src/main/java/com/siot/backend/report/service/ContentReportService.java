package com.siot.backend.report.service;

import com.siot.backend.global.common.PageResponse;
import com.siot.backend.global.exception.BusinessException;
import com.siot.backend.global.exception.ErrorCode;
import com.siot.backend.report.domain.ContentReport;
import com.siot.backend.report.dto.ContentReportAdminUpdateRequest;
import com.siot.backend.report.dto.ContentReportRequest;
import com.siot.backend.report.dto.ContentReportResponse;
import com.siot.backend.report.repository.ContentReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentReportService {

    private final ContentReportRepository contentReportRepository;

    /**
     * 회원 콘텐츠 오류 신고 접수 (COM-01)
     */
    @Transactional
    public ContentReportResponse createReport(Long reporterUserId, ContentReportRequest request) {
        ContentReport report = ContentReport.create(
                reporterUserId,
                request.eventId(),
                request.guideId(),
                request.targetTitleSnapshot(),
                request.quotedText(),
                request.language(),
                request.reason(),
                request.description()
        );
        return ContentReportResponse.from(contentReportRepository.save(report));
    }

    /**
     * 관리자용 신고 페이징 목록 조회 (ADM-07)
     */
    public PageResponse<ContentReportResponse> getReports(String status, Pageable pageable) {
        Page<ContentReport> page;
        if (status != null && !status.isBlank() && !status.equalsIgnoreCase("ALL")) {
            page = contentReportRepository.findByStatusOrderByCreatedAtDesc(status.toUpperCase(), pageable);
        } else {
            page = contentReportRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return PageResponse.from(page, ContentReportResponse::from);
    }

    /**
     * 관리자용 신고 단건 조회
     */
    public ContentReportResponse getReport(Long reportId) {
        ContentReport report = contentReportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTENT_REPORT_NOT_FOUND));
        return ContentReportResponse.from(report);
    }

    /**
     * 관리자용 신고 상태 변경 및 메모 작성 (ADM-07)
     */
    @Transactional
    public ContentReportResponse processReport(Long reportId, ContentReportAdminUpdateRequest request, Long adminUserId) {
        ContentReport report = contentReportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTENT_REPORT_NOT_FOUND));

        report.process(request.status().toUpperCase(), request.adminMemo(), adminUserId, OffsetDateTime.now());
        return ContentReportResponse.from(report);
    }
}
