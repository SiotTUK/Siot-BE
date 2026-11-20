package com.siot.backend.report.service;

import com.siot.backend.global.common.PageResponse;
import com.siot.backend.report.domain.ContentReport;
import com.siot.backend.report.dto.ContentReportAdminUpdateRequest;
import com.siot.backend.report.dto.ContentReportRequest;
import com.siot.backend.report.dto.ContentReportResponse;
import com.siot.backend.report.repository.ContentReportRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ContentReportServiceTest {

    @Mock
    private ContentReportRepository contentReportRepository;

    @InjectMocks
    private ContentReportService contentReportService;

    @Test
    @DisplayName("콘텐츠 오류 신고 접수 검증")
    void createReport_success() {
        // given
        Long userId = 1L;
        ContentReportRequest req = new ContentReportRequest(
                10L, null, "외국인등록", "수수료 3만원", "ko", "정보 오류", "수수료 인상됨"
        );

        given(contentReportRepository.save(any(ContentReport.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        ContentReportResponse res = contentReportService.createReport(userId, req);

        // then
        assertThat(res.reporterUserId()).isEqualTo(userId);
        assertThat(res.guideId()).isEqualTo(10L);
        assertThat(res.status()).isEqualTo("RECEIVED");
        assertThat(res.reason()).isEqualTo("정보 오류");
    }

    @Test
    @DisplayName("관리자 오류 신고 처리 상태 갱신 검증")
    void processReport_success() {
        // given
        Long reportId = 1L;
        Long adminId = 99L;
        ContentReport report = ContentReport.create(
                1L, null, 10L, "외국인등록", "수수료", "ko", "오류", "내용 수정 요청"
        );

        given(contentReportRepository.findById(reportId)).willReturn(Optional.of(report));

        ContentReportAdminUpdateRequest updateReq = new ContentReportAdminUpdateRequest("RESOLVED", "수정 완료");

        // when
        ContentReportResponse res = contentReportService.processReport(reportId, updateReq, adminId);

        // then
        assertThat(res.status()).isEqualTo("RESOLVED");
        assertThat(res.adminMemo()).isEqualTo("수정 완료");
        assertThat(res.processedBy()).isEqualTo(adminId);
        assertThat(res.processedAt()).isNotNull();
    }
}
