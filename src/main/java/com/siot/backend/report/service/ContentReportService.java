package com.siot.backend.report.service;

import com.siot.backend.checklist.domain.Guide;
import com.siot.backend.checklist.domain.GuideTranslation;
import com.siot.backend.checklist.repository.GuideRepository;
import com.siot.backend.checklist.repository.GuideTranslationRepository;
import com.siot.backend.event.domain.Event;
import com.siot.backend.event.domain.EventTranslation;
import com.siot.backend.event.repository.EventRepository;
import com.siot.backend.event.repository.EventTranslationRepository;
import com.siot.backend.global.common.PageResponse;
import com.siot.backend.global.common.SupportedLanguages;
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

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentReportService {

    static final Set<String> STATUSES = Set.of("RECEIVED", "IN_REVIEW", "RESOLVED", "REJECTED");

    private final ContentReportRepository contentReportRepository;
    private final EventRepository eventRepository;
    private final EventTranslationRepository eventTranslationRepository;
    private final GuideRepository guideRepository;
    private final GuideTranslationRepository guideTranslationRepository;
    /** 한국 시간 (ClockConfig) */
    private final Clock clock;

    /**
     * 회원 콘텐츠 오류 신고 접수 (COM-01). 대상은 행사 또는 생활수칙 중 하나이고, 지운 대상은 신고할 수 없다.
     * 대상 제목을 보내지 않으면 대상의 한국어 제목을 남긴다 (대상이 나중에 지워져도 관리자가 알아볼 수 있게).
     */
    @Transactional
    public ContentReportResponse createReport(Long reporterUserId, ContentReportRequest request) {
        if ((request.eventId() == null) == (request.guideId() == null)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "신고 대상은 행사(eventId) 또는 생활수칙(guideId) 중 하나입니다.");
        }
        String title = request.eventId() != null ? eventTitle(request.eventId()) : ruleTitle(request.guideId());
        String snapshot = request.targetTitleSnapshot() == null || request.targetTitleSnapshot().isBlank()
                ? title : request.targetTitleSnapshot().strip();

        ContentReport report = ContentReport.create(
                reporterUserId,
                request.eventId(),
                request.guideId(),
                snapshot,
                request.quotedText().strip(),
                SupportedLanguages.orDefault(request.language()),
                request.reason(),
                request.description() == null || request.description().isBlank() ? null : request.description().strip()
        );
        return ContentReportResponse.from(contentReportRepository.save(report));
    }

    /** 보이는 행사의 한국어 제목. 없거나 지운 행사면 404 */
    private String eventTitle(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));
        return eventTranslationRepository.findOriginal(event.getId()).map(EventTranslation::getTitle).orElse(null);
    }

    /** 생활수칙(RULE)의 한국어 제목. 없거나 지웠거나 생활수칙이 아니면 404 */
    private String ruleTitle(Long guideId) {
        Guide guide = guideRepository.findById(guideId)
                .filter(found -> "RULE".equals(found.getKind()) && !found.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.GUIDE_NOT_FOUND));
        return guideTranslationRepository.findByGuideIdAndLanguage(guide.getId(), "ko")
                .map(GuideTranslation::getTitle).orElse(guide.getCode());
    }

    /**
     * 관리자용 신고 페이징 목록 조회 (ADM-07). status 가 비었거나 ALL 이면 전체
     */
    public PageResponse<ContentReportResponse> getReports(String status, Pageable pageable) {
        Page<ContentReport> page;
        if (status != null && !status.isBlank() && !status.equalsIgnoreCase("ALL")) {
            page = contentReportRepository.findByStatusOrderByCreatedAtDesc(checkStatus(status), pageable);
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
     * 관리자용 신고 상태 변경 및 메모 작성 (ADM-07): 접수(RECEIVED) → 확인 중(IN_REVIEW) → 처리 완료(RESOLVED/REJECTED)
     */
    @Transactional
    public ContentReportResponse processReport(Long reportId, ContentReportAdminUpdateRequest request, Long adminUserId) {
        ContentReport report = contentReportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTENT_REPORT_NOT_FOUND));

        String memo = request.adminMemo() == null || request.adminMemo().isBlank() ? null : request.adminMemo().strip();
        report.process(checkStatus(request.status()), memo, adminUserId, OffsetDateTime.now(clock));
        return ContentReportResponse.from(report);
    }

    private static String checkStatus(String status) {
        String value = status.strip().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(value)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "status 는 RECEIVED, IN_REVIEW, RESOLVED, REJECTED 중 하나입니다.");
        }
        return value;
    }
}
