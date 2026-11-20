package com.siot.backend.event.controller;

import com.siot.backend.auth.security.MemberPrincipal;
import com.siot.backend.event.dto.EventCalendarItemResponse;
import com.siot.backend.event.dto.EventCollectionStatusResponse;
import com.siot.backend.event.dto.EventDetailResponse;
import com.siot.backend.event.dto.EventRecommendationResponse;
import com.siot.backend.event.dto.EventSummaryResponse;
import com.siot.backend.event.service.EventQueryService;
import com.siot.backend.global.common.ApiResponse;
import com.siot.backend.global.common.PageResponse;
import com.siot.backend.global.config.openapi.ApiErrorCodes;
import com.siot.backend.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** EVE-03·04·05·10: 행사 목록·상세·추천·달력·수집 상태 (로그인 필요). 글은 회원 언어, 번역이 없으면 한국어 원문 */
@Tag(name = "7-2. 행사", description = "행사 목록·상세·추천·달력과 최종 갱신 시각 (EVE-03~05, EVE-10). 글은 회원 언어이고 "
        + "번역이 없으면 한국어 원문과 translated=false (COM-02 '번역 준비 중'). 지운 행사는 보이지 않는다")
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventQueryService eventQueryService;

    @Operation(summary = "EVE-10 행사 목록",
            description = "기본은 진행 예정·진행 중(종료일 >= 오늘) 행사를 시작일이 가까운 순으로. past=true 면 지난 행사(종료일 < 오늘)를 "
                    + "최근에 끝난 순으로 (\"지난 행사 보기\"). from·to 는 그 기간과 하루라도 겹치는 행사. "
                    + "결과가 없으면 content 가 빈 배열 (화면은 필터 초기화 버튼). 최종 갱신 시각은 collection-status.")
    @ApiErrorCodes(ErrorCode.INVALID_INPUT)
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<EventSummaryResponse>>> list(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Parameter(description = "지역. 비우면 전체", schema = @Schema(allowableValues = {"SIHEUNG", "GYEONGGI"}))
            @RequestParam(required = false) String region,
            @Parameter(description = "기간 시작 (yyyy-MM-dd)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "기간 끝 (yyyy-MM-dd)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "true 면 지난 행사") @RequestParam(defaultValue = "false") boolean past,
            @Parameter(description = "페이지 (0부터)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기 (최대 100)") @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.of(
                eventQueryService.list(principal.getId(), region, from, to, past, page, size)));
    }

    @Operation(summary = "EVE-05 행사 추천",
            description = "진행 예정·진행 중 행사 중에서. 온보딩 답으로 태그를 정한다: 거주 정보 '자녀'(KIDS) → FAMILY_KIDS, "
                    + "'한국어 공부'(KOREAN) → KOREAN_CLASS, 사용자 유형 '처음 입국'(NEW) → NEWCOMER. "
                    + "순서: 맞는 태그가 있는 행사(많은 순) → 다문화 행사 → 시흥 행사 → 시작일 가까운 순. "
                    + "온보딩 전이면 다문화 → 시흥 → 시작일 순. 항목마다 추천 이유(reasons).")
    @GetMapping("/recommendations")
    public ResponseEntity<ApiResponse<List<EventRecommendationResponse>>> recommendations(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Parameter(description = "개수 (1~20)") @RequestParam(defaultValue = "3") int size) {
        return ResponseEntity.ok(ApiResponse.of(eventQueryService.recommendations(principal.getId(), size)));
    }

    @Operation(summary = "EVE-03 행사 달력",
            description = "그 달에 하루라도 걸치는 행사 (지난 행사 포함). 생활 달력 화면은 이 API 와 일정 API 를 따로 불러 합친다.")
    @ApiErrorCodes(ErrorCode.INVALID_INPUT)
    @GetMapping("/calendar")
    public ResponseEntity<ApiResponse<List<EventCalendarItemResponse>>> calendar(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Parameter(description = "연도", example = "2026") @RequestParam int year,
            @Parameter(description = "월 (1~12)", example = "12") @RequestParam int month) {
        return ResponseEntity.ok(ApiResponse.of(eventQueryService.calendar(principal.getId(), year, month)));
    }

    @Operation(summary = "EVE-10 행사 최종 갱신 시각",
            description = "출처(GGC 경기문화재단, FESTIVAL_STD 전국문화축제표준데이터, TOURAPI 한국관광공사)별 마지막 성공 시각과 "
                    + "마지막 실행 결과. lastUpdatedAt 은 그중 가장 최근 성공 시각.")
    @GetMapping("/collection-status")
    public ResponseEntity<ApiResponse<EventCollectionStatusResponse>> collectionStatus() {
        return ResponseEntity.ok(ApiResponse.of(eventQueryService.collectionStatus()));
    }

    @Operation(summary = "EVE-04 행사 상세",
            description = "일시·장소·가격·신청 방법(마감·링크)·설명·원문 링크·지역·다문화 여부·내 알림 신청 여부. 값이 없는 칸은 null "
                    + "(수집 행사는 장소·설명이 없는 경우가 많아 원문 링크로 안내). 지운 행사는 404.")
    @ApiErrorCodes(ErrorCode.EVENT_NOT_FOUND)
    @GetMapping("/{eventId:\\d+}")
    public ResponseEntity<ApiResponse<EventDetailResponse>> detail(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long eventId) {
        return ResponseEntity.ok(ApiResponse.of(eventQueryService.detail(principal.getId(), eventId)));
    }
}
