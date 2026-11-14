package com.siot.backend.rag.controller;

import com.siot.backend.auth.security.MemberPrincipal;
import com.siot.backend.global.common.ApiResponse;
import com.siot.backend.global.common.PageResponse;
import com.siot.backend.rag.dto.ChatReviewResponse;
import com.siot.backend.rag.dto.ChatReviewUpdateRequest;
import com.siot.backend.rag.service.ChatReviewService;
import com.siot.backend.rag.service.ChatReviewService.ReviewType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** ADM-08: 부정 평가 답변·미응답 질문 (관리자 전용) */
@RestController
@RequestMapping("/api/admin/chat/reviews")
@RequiredArgsConstructor
public class AdminChatReviewController {

    private final ChatReviewService chatReviewService;

    /**
     * @param type     ALL(기본) / NEGATIVE / NO_EVIDENCE
     * @param reviewed false = 미처리만, true = 처리 완료만, 생략 = 전체
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ChatReviewResponse>>> list(
            @RequestParam(defaultValue = "ALL") ReviewType type,
            @RequestParam(required = false) Boolean reviewed,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return ResponseEntity.ok(ApiResponse.of(PageResponse.from(chatReviewService.list(type, reviewed, pageable))));
    }

    @PatchMapping("/{messageId}")
    public ResponseEntity<ApiResponse<ChatReviewResponse>> update(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long messageId,
            @Valid @RequestBody ChatReviewUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.of(
                chatReviewService.update(principal.getId(), messageId, request.reviewed(), request.adminMemo())));
    }
}
