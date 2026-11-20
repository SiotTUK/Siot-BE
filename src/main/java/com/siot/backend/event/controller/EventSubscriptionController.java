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

    @Operation(summary = "행사 알림 신청 (NTF-02, EVE-06)")
    @PostMapping("/api/events/{eventId}/subscriptions")
    public ResponseEntity<ApiResponse<EventSubscriptionResponse>> subscribe(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long eventId
    ) {
        return ResponseEntity.ok(ApiResponse.of(subscriptionService.subscribe(principal.getId(), eventId)));
    }

    @Operation(summary = "행사 알림 해제 (NTF-03, EVE-06)")
    @DeleteMapping("/api/events/{eventId}/subscriptions")
    public ResponseEntity<ApiResponse<EventSubscriptionResponse>> unsubscribe(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long eventId
    ) {
        return ResponseEntity.ok(ApiResponse.of(subscriptionService.unsubscribe(principal.getId(), eventId)));
    }

    @Operation(summary = "행사 알림 신청 여부 확인")
    @GetMapping("/api/events/{eventId}/subscriptions")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> isSubscribed(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long eventId
    ) {
        boolean subscribed = subscriptionService.isSubscribed(principal.getId(), eventId);
        return ResponseEntity.ok(ApiResponse.of(Map.of("subscribed", subscribed)));
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
