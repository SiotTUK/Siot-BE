package com.siot.backend.notification.controller;

import com.siot.backend.auth.security.MemberPrincipal;
import com.siot.backend.global.common.ApiResponse;
import com.siot.backend.global.common.PageResponse;
import com.siot.backend.global.config.openapi.ApiErrorCodes;
import com.siot.backend.global.config.openapi.SuccessStatus;
import com.siot.backend.global.exception.ErrorCode;
import com.siot.backend.notification.dto.NotificationPreferenceResponse;
import com.siot.backend.notification.dto.NotificationPreferenceUpdateRequest;
import com.siot.backend.notification.dto.NotificationResponse;
import com.siot.backend.notification.dto.UnreadCountResponse;
import com.siot.backend.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "8-1. 알림", description = "알림 목록, 읽음 처리, 알림 환경설정 (NTF-01~05)")
@RestController
@RequestMapping("/api/members/me/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "내 알림 목록 페이징 조회 (NTF-04)")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> getNotifications(
            @AuthenticationPrincipal MemberPrincipal principal,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.of(notificationService.getNotifications(principal.getId(), pageable)));
    }

    @Operation(summary = "안 읽은 알림 개수 조회")
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> getUnreadCount(
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        long count = notificationService.getUnreadCount(principal.getId());
        return ResponseEntity.ok(ApiResponse.of(new UnreadCountResponse(count)));
    }

    @Operation(summary = "알림 단건 읽음 처리 (NTF-04)")
    @ApiErrorCodes({ErrorCode.NOTIFICATION_NOT_FOUND})
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markRead(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long notificationId
    ) {
        return ResponseEntity.ok(ApiResponse.of(notificationService.markRead(principal.getId(), notificationId)));
    }

    @Operation(summary = "알림 전체 읽음 처리")
    @SuccessStatus(value = 204, description = "모두 읽음 처리 완료")
    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllRead(
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        notificationService.markAllRead(principal.getId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "알림 수신 설정 목록 조회")
    @GetMapping("/preferences")
    public ResponseEntity<ApiResponse<List<NotificationPreferenceResponse>>> getPreferences(
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.of(notificationService.getPreferences(principal.getId())));
    }

    @Operation(summary = "알림 수신 설정 변경 (NTF-03)")
    @PutMapping("/preferences")
    public ResponseEntity<ApiResponse<NotificationPreferenceResponse>> updatePreference(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody NotificationPreferenceUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.of(notificationService.updatePreference(principal.getId(), request)));
    }

    @Operation(summary = "특정 알림 종류 수신 설정 변경 (NTF-03)")
    @PutMapping("/preferences/{kind}")
    public ResponseEntity<ApiResponse<NotificationPreferenceResponse>> updatePreferenceByKind(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable String kind,
            @RequestBody NotificationPreferenceUpdateRequest request
    ) {
        NotificationPreferenceUpdateRequest updated = new NotificationPreferenceUpdateRequest(
                kind,
                request.enabled() != null ? request.enabled() : true,
                request.leadDays(),
                request.emailEnabled(),
                request.leadDaysList()
        );
        return ResponseEntity.ok(ApiResponse.of(notificationService.updatePreference(principal.getId(), updated)));
    }
}
