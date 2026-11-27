package com.siot.backend.schedule.controller;

import com.siot.backend.auth.security.MemberPrincipal;
import com.siot.backend.global.common.ApiResponse;
import com.siot.backend.global.common.SupportedLanguages;
import com.siot.backend.global.config.openapi.ApiErrorCodes;
import com.siot.backend.global.exception.ErrorCode;
import com.siot.backend.schedule.dto.MonthlyTodoResponse;
import com.siot.backend.schedule.dto.ScheduleResponse;
import com.siot.backend.schedule.dto.TaxDetailResponse;
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
            description = "조회 월과 기간이 겹치는 공통 일정과 로그인 회원의 개인 일정을 함께 반환합니다. "
                    + "개인 일정의 제목과 대상 안내는 요청 언어로 표시합니다.")
    @ApiErrorCodes(ErrorCode.INVALID_INPUT)
    @GetMapping("/monthly")
    public ResponseEntity<ApiResponse<List<ScheduleResponse>>> getMonthly(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Parameter(description = "연도 (2000~2100, 예: 2026). 생략 시 올해") @RequestParam(required = false) Integer year,
            @Parameter(description = "월 (1~12). 생략 시 이번 달") @RequestParam(required = false) Integer month,
            @Parameter(description = "언어 (ko/en/zh/vi). 생략 시 ko, 그 밖의 값은 400") @RequestParam(required = false) String language) {

        LocalDate now = LocalDate.now(SEOUL_ZONE);
        int targetYear = year != null ? year : now.getYear();
        int targetMonth = month != null ? month : now.getMonthValue();
        Long userId = principal != null ? principal.getId() : null;

        List<ScheduleResponse> response = calendarScheduleService.getMonthlySchedules(userId, targetYear, targetMonth,
                SupportedLanguages.requireValid(language));
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @Operation(summary = "CAL-02 이번 달 할일",
            description = "메인에 보여 줄 이번 달 일정 중 마감이 지나지 않은 것과 마감까지 남은 일수(dDay)를 마감 임박 순으로 줍니다. "
                    + "이번 달에 남은 일정이 없으면 다음 달 일정을 nextMonth=true 로 줍니다. 로그인하면 개인 일정(체류 만료 등)도 넣습니다.")
    @ApiErrorCodes(ErrorCode.INVALID_INPUT)
    @GetMapping("/this-month")
    public ResponseEntity<ApiResponse<MonthlyTodoResponse>> getThisMonthTodos(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Parameter(description = "언어 (ko/en/zh/vi). 생략 시 ko, 그 밖의 값은 400") @RequestParam(required = false) String language) {
        Long userId = principal != null ? principal.getId() : null;
        return ResponseEntity.ok(ApiResponse.of(
                calendarScheduleService.getMonthlyTodos(userId, SupportedLanguages.requireValid(language), LocalDate.now(SEOUL_ZONE))));
    }

    @Operation(summary = "CAL-01 연간 일정 조회",
            description = "해당 연도의 공통 세금/생활 일정(시작일 기준)에 로그인 회원의 개인 일정을 월간과 같은 규칙으로 넣어 줍니다: "
                    + "체류 만료·연장 신청 시작, 자차 → 자동차세(6·12월), 자가 → 재산세(7·9월). dDay·personal·highlighted 도 월간과 같습니다.")
    @ApiErrorCodes(ErrorCode.INVALID_INPUT)
    @GetMapping("/yearly")
    public ResponseEntity<ApiResponse<List<ScheduleResponse>>> getYearly(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Parameter(description = "연도 (2000~2100, 예: 2026). 생략 시 올해") @RequestParam(required = false) Integer year,
            @Parameter(description = "언어 (ko/en/zh/vi). 생략 시 ko, 그 밖의 값은 400") @RequestParam(required = false) String language) {

        int targetYear = year != null ? year : LocalDate.now(SEOUL_ZONE).getYear();
        Long userId = principal != null ? principal.getId() : null;
        List<ScheduleResponse> response = calendarScheduleService.getYearlySchedules(userId, targetYear,
                SupportedLanguages.requireValid(language));
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @Operation(summary = "CAL-03 세금 상세 안내 목록 조회",
            description = "자동차세, 재산세, 주민세 등 지방세 상세 납부 안내 목록을 조회합니다. kind 를 주면 그 세금의 공통 일정만 "
                    + "(생활수칙 세금 항목의 taxKind 로 상세를 찾을 때, RUL-06). 납부 시기가 여럿이면 회차마다 하나씩, 시작일 순. "
                    + "대응하는 일정이 없으면 빈 목록")
    @ApiErrorCodes(ErrorCode.INVALID_INPUT)
    @GetMapping("/taxes")
    public ResponseEntity<ApiResponse<List<TaxDetailResponse>>> getTaxSchedules(
            @Parameter(description = "언어 (ko/en/zh/vi). 생략 시 ko, 그 밖의 값은 400") @RequestParam(required = false) String language,
            @Parameter(description = "세금 종류 (TAX_CAR / TAX_PROPERTY / TAX_RESIDENT). 생략하면 전체", example = "TAX_CAR")
            @RequestParam(required = false) String kind) {
        return ResponseEntity.ok(ApiResponse.of(
                calendarScheduleService.getTaxSchedules(SupportedLanguages.requireValid(language), kind)));
    }

    @Operation(summary = "CAL-03 세금 단건 상세 안내 조회", description = "특정 세금 일정의 과세 기준, 납부 기간, 세율, 가산세, 납부 링크 등 상세 정보를 조회합니다.")
    @ApiErrorCodes({ErrorCode.SCHEDULE_NOT_FOUND, ErrorCode.INVALID_INPUT})
    @GetMapping("/taxes/{scheduleId}")
    public ResponseEntity<ApiResponse<TaxDetailResponse>> getTaxDetail(
            @Parameter(description = "일정 ID") @PathVariable Long scheduleId,
            @Parameter(description = "언어 (ko/en/zh/vi). 생략 시 ko, 그 밖의 값은 400") @RequestParam(required = false) String language) {
        return ResponseEntity.ok(ApiResponse.of(calendarScheduleService.getTaxDetail(scheduleId, SupportedLanguages.requireValid(language))));
    }

    @Operation(summary = "CAL-03 일정/세금 단건 상세 조회")
    @ApiErrorCodes({ErrorCode.SCHEDULE_NOT_FOUND, ErrorCode.INVALID_INPUT})
    @GetMapping("/{scheduleId}")
    public ResponseEntity<ApiResponse<TaxDetailResponse>> getScheduleDetail(
            @Parameter(description = "일정 ID") @PathVariable Long scheduleId,
            @Parameter(description = "언어 (ko/en/zh/vi). 생략 시 ko, 그 밖의 값은 400") @RequestParam(required = false) String language) {
        return ResponseEntity.ok(ApiResponse.of(calendarScheduleService.getTaxDetail(scheduleId, SupportedLanguages.requireValid(language))));
    }
}
