package com.siot.backend.schedule.controller;

import com.siot.backend.auth.security.MemberPrincipal;
import com.siot.backend.global.common.ApiResponse;
import com.siot.backend.schedule.dto.ScheduleResponse;
import com.siot.backend.schedule.service.CalendarScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 생활 달력 & 세금 상세 일정 REST API (CAL-01 ~ CAL-04)
 */
@Tag(name = "7. 생활 달력", description = "공통 세금 일정, 로그인 회원의 체류 만료일 및 자동차세/재산세 일정 병합 (CAL-01~04)")
@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
public class CalendarScheduleController {

    private final CalendarScheduleService calendarScheduleService;
    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    @Operation(summary = "CAL-02 월간 생활 및 세금 일정 조회",
            description = "공통 세금 일정과 로그인 회원의 체류만료일 및 자동차세/재산세 납부 일정을 병합하여 반환합니다.")
    @GetMapping("/monthly")
    public ResponseEntity<ApiResponse<List<ScheduleResponse>>> getMonthly(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Parameter(description = "연도 (예: 2026). 생략 시 올해") @RequestParam(required = false) Integer year,
            @Parameter(description = "월 (1~12). 생략 시 이번 달") @RequestParam(required = false) Integer month,
            @Parameter(description = "언어 (ko/en/zh/vi). 생략 시 ko") @RequestParam(required = false) String language) {

        LocalDate now = LocalDate.now(SEOUL_ZONE);
        int targetYear = year != null ? year : now.getYear();
        int targetMonth = month != null ? month : now.getMonthValue();
        Long userId = principal != null ? principal.getId() : null;

        List<ScheduleResponse> response = calendarScheduleService.getMonthlySchedules(userId, targetYear, targetMonth, language);
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @Operation(summary = "CAL-01 연간 일정 조회", description = "해당 연도의 전체 세금/생활 일정을 조회합니다.")
    @GetMapping("/yearly")
    public ResponseEntity<ApiResponse<List<ScheduleResponse>>> getYearly(
            @Parameter(description = "연도 (예: 2026). 생략 시 올해") @RequestParam(required = false) Integer year,
            @Parameter(description = "언어 (ko/en/zh/vi). 생략 시 ko") @RequestParam(required = false) String language) {

        int targetYear = year != null ? year : LocalDate.now(SEOUL_ZONE).getYear();
        List<ScheduleResponse> response = calendarScheduleService.getYearlySchedules(targetYear, language);
        return ResponseEntity.ok(ApiResponse.of(response));
    }
}
