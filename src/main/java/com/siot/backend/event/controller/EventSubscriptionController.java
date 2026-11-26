package com.siot.backend.event.controller;

import com.siot.backend.auth.security.MemberPrincipal;
import com.siot.backend.event.dto.EventSubscriptionResponse;
import com.siot.backend.event.service.EventSubscriptionService;
import com.siot.backend.global.common.ApiResponse;
import com.siot.backend.global.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "행사 알림", description = "문화행사 시작 사전 알림 신청·해제 (NTF-02·03, EVE-06)")
@RestController
@RequiredArgsConstructor
public class EventSubscriptionController {

    private final EventSubscriptionService subscriptionService;

    @Operation(summary = "행사 알림 신청 (NTF-02, EVE-06)",
            description = "leadDays 로 알림 받을 시점을 고른다: 1(시작 24시간 전, 기본) / 3 / 7(시작 N일 전 오전 9시). "
                    + "이미 신청했으면 다시 보내 시점을 바꿀 수 있다 (생략하면 그대로). 신청 마감 D-1·일정 변경 알림은 시점과 상관없이 보낸다.")
    @PostMapping("/api/events/{eventId}/subscriptions")
    public ResponseEntity<ApiResponse<EventSubscriptionResponse>> subscribe(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long eventId,
            @io.swagger.v3.oas.annotations.Parameter(description = "행사 시작 며칠 전에 알릴지 (1, 3, 7)",
                    schema = @io.swagger.v3.oas.annotations.media.Schema(allowableValues = {"1", "3", "7"}))
            @RequestParam(required = false) Integer leadDays
    ) {
        return ResponseEntity.ok(ApiResponse.of(subscriptionService.subscribe(principal.getId(), eventId, leadDays)));
    }

    @Operation(summary = "행사 알림 해제 (NTF-03, EVE-06)")
    @DeleteMapping("/api/events/{eventId}/subscriptions")
    public ResponseEntity<ApiResponse<EventSubscriptionResponse>> unsubscribe(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long eventId
    ) {
        return ResponseEntity.ok(ApiResponse.of(subscriptionService.unsubscribe(principal.getId(), eventId)));
    }

    @Operation(summary = "행사 알림 신청 여부 확인",
            description = "{ subscribed, leadDays } — leadDays 는 신청한 알림 시점 (신청 안 했으면 null)")
    @GetMapping("/api/events/{eventId}/subscriptions")
    public ResponseEntity<ApiResponse<Map<String, Object>>> isSubscribed(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long eventId
    ) {
        Integer leadDays = subscriptionService.leadDays(principal.getId(), eventId);
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("subscribed", leadDays != null);
        body.put("leadDays", leadDays);
        return ResponseEntity.ok(ApiResponse.of(body));
    }

    @Operation(summary = "내 알림 신청(구독) 행사 목록 조회")
    @GetMapping("/api/members/me/event-subscriptions")
    public ResponseEntity<ApiResponse<PageResponse<EventSubscriptionResponse>>> getMySubscriptions(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.of(subscriptionService.getMySubscriptions(principal.getId(), pageable)));
    }
}
