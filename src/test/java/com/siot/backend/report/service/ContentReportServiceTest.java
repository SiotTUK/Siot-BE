package com.siot.backend.report.service;

import com.siot.backend.checklist.domain.Guide;
import com.siot.backend.checklist.domain.GuideTranslation;
import com.siot.backend.checklist.domain.ItemType;
import com.siot.backend.checklist.repository.GuideRepository;
import com.siot.backend.checklist.repository.GuideTranslationRepository;
import com.siot.backend.event.domain.Event;
import com.siot.backend.event.domain.EventTranslation;
import com.siot.backend.event.repository.EventRepository;
import com.siot.backend.event.repository.EventTranslationRepository;
import com.siot.backend.global.exception.BusinessException;
import com.siot.backend.global.exception.ErrorCode;
import com.siot.backend.report.domain.ContentReport;
import com.siot.backend.report.dto.ContentReportAdminUpdateRequest;
import com.siot.backend.report.dto.ContentReportRequest;
import com.siot.backend.report.dto.ContentReportResponse;
import com.siot.backend.report.repository.ContentReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** COM-01 오류 신고 접수, ADM-07 처리 */
@ExtendWith(MockitoExtension.class)
class ContentReportServiceTest {

    @Mock
    private ContentReportRepository contentReportRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private EventTranslationRepository eventTranslationRepository;
    @Mock
    private GuideRepository guideRepository;
    @Mock
    private GuideTranslationRepository guideTranslationRepository;

    private ContentReportService contentReportService;

    @BeforeEach
    void setUp() {
        contentReportService = new ContentReportService(contentReportRepository, eventRepository,
                eventTranslationRepository, guideRepository, guideTranslationRepository,
                Clock.fixed(Instant.parse("2026-11-26T03:00:00Z"), ZoneId.of("Asia/Seoul")));
    }

    private static Guide rule(long id, String kind) {
        Guide guide = Guide.create("RUL-01", kind, ItemType.INFO, "쓰레기 배출", 1, null, false);
        ReflectionTestUtils.setField(guide, "id", id);
        return guide;
    }

    private static ContentReportRequest request(Long guideId, Long eventId, String title, String language) {
        return new ContentReportRequest(guideId, eventId, title, " 수수료 3만원 ", language, "CONTENT", "  ");
    }

    @Test
    @DisplayName("생활수칙 신고: 대상 제목을 보내지 않으면 한국어 제목을 남기고, 언어가 없으면 ko")
    void createRuleReport_fillsTitleAndLanguage() {
        given(guideRepository.findById(10L)).willReturn(Optional.of(rule(10L, "RULE")));
        given(guideTranslationRepository.findByGuideIdAndLanguage(10L, "ko")).willReturn(Optional.of(
                GuideTranslation.create(10L, "ko", "음식물 쓰레기", null, "본문", null, null, null, null, null, null, null)));
        given(contentReportRepository.save(any(ContentReport.class))).willAnswer(inv -> inv.getArgument(0));

        ContentReportResponse res = contentReportService.createReport(1L, request(10L, null, null, null));

        assertThat(res.reporterUserId()).isEqualTo(1L);
        assertThat(res.targetType()).isEqualTo("RULE");
        assertThat(res.targetTitleSnapshot()).isEqualTo("음식물 쓰레기");
        assertThat(res.quotedText()).isEqualTo("수수료 3만원");
        assertThat(res.language()).isEqualTo("ko");
        assertThat(res.reason()).isEqualTo("CONTENT");
        assertThat(res.description()).isNull();
        assertThat(res.status()).isEqualTo("RECEIVED");
    }

    @Test
    @DisplayName("행사 신고: 지운 행사는 404, 보이는 행사는 한국어 행사명을 남긴다")
    void createEventReport() {
        Event active = Event.manual(1L);
        ReflectionTestUtils.setField(active, "id", 5L);
        given(eventRepository.findById(5L)).willReturn(Optional.of(active));
        given(eventTranslationRepository.findOriginal(5L))
                .willReturn(Optional.of(new EventTranslation(5L, "ko", "시흥갯골축제", null, null, 1)));
        given(contentReportRepository.save(any(ContentReport.class))).willAnswer(inv -> inv.getArgument(0));

        ContentReportResponse res = contentReportService.createReport(1L, request(null, 5L, null, "en"));
        assertThat(res.targetType()).isEqualTo("EVENT");
        assertThat(res.targetTitleSnapshot()).isEqualTo("시흥갯골축제");
        assertThat(res.language()).isEqualTo("en");

        Event deleted = Event.manual(1L);
        deleted.delete(1L);
        given(eventRepository.findById(6L)).willReturn(Optional.of(deleted));
        assertThatThrownBy(() -> contentReportService.createReport(1L, request(null, 6L, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.EVENT_NOT_FOUND);
    }

    @Test
    @DisplayName("대상은 행사·생활수칙 중 하나: 둘 다 없거나 둘 다 있으면 400, 체크리스트 항목이나 없는 항목은 404")
    void rejectsInvalidTargets() {
        assertThatThrownBy(() -> contentReportService.createReport(1L, request(null, null, null, null)))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
        assertThatThrownBy(() -> contentReportService.createReport(1L, request(10L, 5L, null, null)))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);

        given(guideRepository.findById(20L)).willReturn(Optional.of(rule(20L, "CHECKLIST")));
        assertThatThrownBy(() -> contentReportService.createReport(1L, request(20L, null, null, null)))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.GUIDE_NOT_FOUND);
        given(guideRepository.findById(99L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> contentReportService.createReport(1L, request(99L, null, null, null)))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.GUIDE_NOT_FOUND);
        verify(contentReportRepository, never()).save(any());
    }

    @Test
    @DisplayName("관리자 처리: 상태(대소문자 무시)와 메모를 기록하고, 모르는 상태는 400")
    void processReport() {
        ContentReport report = ContentReport.create(1L, null, 10L, "외국인등록", "수수료", "ko", "CONTENT", null);
        given(contentReportRepository.findById(1L)).willReturn(Optional.of(report));

        ContentReportResponse res = contentReportService.processReport(1L,
                new ContentReportAdminUpdateRequest("in_review", " 확인 중 "), 99L);
        assertThat(res.status()).isEqualTo("IN_REVIEW");
        assertThat(res.adminMemo()).isEqualTo("확인 중");
        assertThat(res.processedBy()).isEqualTo(99L);
        assertThat(res.processedAt()).isEqualTo(Instant.parse("2026-11-26T03:00:00Z").atOffset(java.time.ZoneOffset.ofHours(9)));

        assertThatThrownBy(() -> contentReportService.processReport(1L,
                new ContentReportAdminUpdateRequest("DONE", null), 99L))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
    }
}
