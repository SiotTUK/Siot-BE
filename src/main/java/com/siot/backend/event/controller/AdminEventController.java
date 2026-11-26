package com.siot.backend.event.controller;

import com.siot.backend.auth.security.MemberPrincipal;
import com.siot.backend.event.dto.AdminEventCreateRequest;
import com.siot.backend.event.dto.AdminEventResponse;
import com.siot.backend.event.dto.AdminEventSummaryResponse;
import com.siot.backend.event.dto.AdminEventUpdateRequest;
import com.siot.backend.event.dto.EventCollectionRunResponse;
import com.siot.backend.event.service.AdminEventService;
import com.siot.backend.global.common.ApiResponse;
import com.siot.backend.global.common.PageResponse;
import com.siot.backend.global.config.openapi.ApiErrorCodes;
import com.siot.backend.global.config.openapi.SuccessStatus;
import com.siot.backend.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** ADM-05, EVE-07~09: 행사 등록·수정·삭제와 수동 수집 (관리자 전용) */
@Tag(name = "9-5. 관리자: 행사", description = "행사 목록·등록·수정·삭제, 수집 바로 실행 (ADM-05, EVE-07~09). 관리자만. "
        + "고친 칸은 잠겨서 매일 수집이 덮어쓰지 않고, 지운 행사는 다시 수집해도 살아나지 않는다")
@RestController
@RequestMapping("/api/admin/events")
@RequiredArgsConstructor
public class AdminEventController {

    private final AdminEventService adminEventService;

    @Operation(summary = "ADM-05 행사 목록",
            description = "지운 행사(DELETED) 포함, 최근 등록 순. 상태·지역·출처·행사명(한국어, 부분 일치)으로 거른다.")
    @ApiErrorCodes(ErrorCode.INVALID_INPUT)
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminEventSummaryResponse>>> list(
            @Parameter(description = "상태", schema = @Schema(allowableValues = {"ACTIVE", "DELETED"}))
            @RequestParam(required = false) String status,
            @Parameter(description = "지역", schema = @Schema(allowableValues = {"SIHEUNG", "GYEONGGI"}))
            @RequestParam(required = false) String region,
            @Parameter(description = "출처", schema = @Schema(allowableValues = {"GGC", "FESTIVAL_STD", "TOURAPI", "MANUAL"}))
            @RequestParam(required = false) String source,
            @Parameter(description = "행사명 검색어") @RequestParam(required = false) String title,
            @Parameter(description = "카테고리", schema = @Schema(allowableValues =
                    {"FESTIVAL", "PERFORMANCE", "EXHIBITION", "EDUCATION", "EVENT"}))
            @RequestParam(required = false) String category,
            @Parameter(description = "페이지 (0부터)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기 (최대 100)") @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.of(
                adminEventService.list(status, region, source, title, category, page, size)));
    }

    @Operation(summary = "ADM-05 행사 상세", description = "한국어 원문과 잠긴 칸, 태그, 번역이 끝난 언어. 지운 행사도 볼 수 있다.")
    @ApiErrorCodes(ErrorCode.EVENT_NOT_FOUND)
    @GetMapping("/{eventId:\\d+}")
    public ResponseEntity<ApiResponse<AdminEventResponse>> get(@PathVariable Long eventId) {
        return ResponseEntity.ok(ApiResponse.of(adminEventService.get(eventId)));
    }

    @Operation(summary = "EVE-07 행사 등록",
            description = "자동 수집되지 않는 행사(다문화·외국인 대상 등)를 한국어로 등록한다. 행사명(연도·회차·기호 무시)·시작일이 같고 "
                    + "장소가 같거나 한쪽이 빈 ACTIVE 행사가 있으면 409 DUPLICATE_EVENT (details.eventIds). "
                    + "확인 후 force=true 로 다시 보내면 등록한다. en/zh/vi 는 저장할 때 자동 번역하고, 실패해도 등록은 성공이며 "
                    + "다음 수집 주기에 다시 번역한다.")
    @SuccessStatus(value = 201, description = "등록됨")
    @ApiErrorCodes({ErrorCode.INVALID_INPUT, ErrorCode.DUPLICATE_EVENT})
    @PostMapping
    public ResponseEntity<ApiResponse<AdminEventResponse>> create(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Parameter(description = "중복 행사가 있어도 등록") @RequestParam(defaultValue = "false") boolean force,
            @Valid @RequestBody AdminEventCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(adminEventService.create(principal.getId(), request, force)));
    }

    @Operation(summary = "EVE-08 행사 수정",
            description = "보낸 칸만 바뀌고, 그 칸은 잠겨서 다시 수집해도 덮어쓰지 않는다 (lockedFields). 내용이 바뀌면 revision +1 이고 "
                    + "번역을 다음 수집 주기에 다시 만든다. 일정이 바뀌면 알림을 신청한 회원에게 일정 변경 알림(NTF-02). "
                    + "태그는 고칠 수 없고 행사명 키워드로 다시 계산한다.")
    @ApiErrorCodes({ErrorCode.INVALID_INPUT, ErrorCode.EVENT_NOT_FOUND})
    @PatchMapping("/{eventId:\\d+}")
    public ResponseEntity<ApiResponse<AdminEventResponse>> update(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long eventId,
            @Valid @RequestBody AdminEventUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.of(adminEventService.update(principal.getId(), eventId, request)));
    }

    @Operation(summary = "EVE-09 행사 삭제",
            description = "status=DELETED 로 바꾼다 (행은 남음). 회원에게 보이지 않고, 다시 수집해도 살아나거나 새로 만들어지지 않는다.")
    @SuccessStatus(value = 204, description = "삭제됨")
    @ApiErrorCodes(ErrorCode.EVENT_NOT_FOUND)
    @DeleteMapping("/{eventId:\\d+}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long eventId) {
        adminEventService.delete(principal.getId(), eventId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "ADM-05 행사 수집 바로 실행",
            description = "매일 03:00 수집과 같은 코드를 지금 한 번 실행한다. 출처 수집이 끝나면 출처별 결과를 돌려주고, 번역은 뒤에서 "
                    + "이어서 한다. 수집이나 번역이 이미 돌고 있으면 409 EVENT_COLLECTION_RUNNING.")
    @ApiErrorCodes(ErrorCode.EVENT_COLLECTION_RUNNING)
    @PostMapping("/collect")
    public ResponseEntity<ApiResponse<List<EventCollectionRunResponse>>> collect() {
        return ResponseEntity.ok(ApiResponse.of(adminEventService.collect()));
    }
}
