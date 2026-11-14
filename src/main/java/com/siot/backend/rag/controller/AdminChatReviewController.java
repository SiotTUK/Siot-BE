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
import com.siot.backend.global.config.openapi.ApiErrorCodes;
import com.siot.backend.global.config.openapi.SuccessStatus;
import com.siot.backend.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/** ADM-08: 부정 평가 답변·미응답 질문 (관리자 전용) */
@Tag(name = "9-2. 관리자: 챗봇 검토", description = "부정 평가 답변·근거 없음 질문 목록과 처리 기록 (ADM-08). 관리자만")
@RestController
@RequestMapping("/api/admin/chat/reviews")
@RequiredArgsConstructor
public class AdminChatReviewController {

    private final ChatReviewService chatReviewService;

    /**
     * @param type     ALL(기본) / NEGATIVE / NO_EVIDENCE
     * @param reviewed false = 미처리만, true = 처리 완료만, 생략 = 전체
     */
    @Operation(summary = "ADM-08 검토 목록",
            description = "최신순. 질문·답변·의견은 개인정보를 가린 값만 보이고 회원 신원 정보는 없다.")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ChatReviewResponse>>> list(
            @Parameter(description = "ALL / NEGATIVE(도움이 안 됐어요) / NO_EVIDENCE(자료에서 답을 못 찾음)")
            @RequestParam(defaultValue = "ALL") ReviewType type,
            @Parameter(description = "false = 미처리만, true = 처리 완료만, 생략 = 전체")
            @RequestParam(required = false) Boolean reviewed,
            @Parameter(description = "페이지 (0부터)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기 (최대 100)") @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return ResponseEntity.ok(ApiResponse.of(PageResponse.from(chatReviewService.list(type, reviewed, pageable))));
    }

    @Operation(summary = "ADM-08 처리 기록", description = "처리 여부와 메모(1000자 이하, 자료 보충 기록). 처리한 관리자와 시각이 저장된다.")
    @ApiErrorCodes(ErrorCode.CHAT_REVIEW_NOT_FOUND)
    @PatchMapping("/{messageId}")
    public ResponseEntity<ApiResponse<ChatReviewResponse>> update(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long messageId,
            @Valid @RequestBody ChatReviewUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.of(
                chatReviewService.update(principal.getId(), messageId, request.reviewed(), request.adminMemo())));
    }
}
