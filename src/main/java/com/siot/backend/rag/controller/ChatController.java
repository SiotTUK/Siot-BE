package com.siot.backend.rag.controller;

import com.siot.backend.auth.security.MemberPrincipal;
import com.siot.backend.global.common.ApiResponse;
import com.siot.backend.global.common.PageResponse;
import com.siot.backend.rag.dto.AskResponse;
import com.siot.backend.rag.dto.ConversationCreateRequest;
import com.siot.backend.rag.dto.ConversationResponse;
import com.siot.backend.rag.dto.MessageResponse;
import com.siot.backend.rag.dto.QuestionRequest;
import com.siot.backend.rag.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 챗봇 (회원 전용). 대화는 본인 것만 조회·사용할 수 있다 (NFR-03). */
@RestController
@RequestMapping("/api/chat/conversations")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /** CB-03: 새 대화 (201) */
    @PostMapping
    public ResponseEntity<ApiResponse<ConversationResponse>> start(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody(required = false) ConversationCreateRequest request) {
        String language = request == null ? null : request.language();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(chatService.startConversation(principal.getId(), language)));
    }

    /** CB-03: 이전 대화 목록 (최근 순) */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ConversationResponse>>> list(
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
        return ResponseEntity.ok(ApiResponse.of(
                PageResponse.from(chatService.listConversations(principal.getId(), pageable))));
    }

    /** CB-03: 대화 내용 */
    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<ApiResponse<List<MessageResponse>>> messages(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long conversationId) {
        return ResponseEntity.ok(ApiResponse.of(chatService.getMessages(principal.getId(), conversationId)));
    }

    /** CB-16: 질문 → 답변 */
    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<ApiResponse<AskResponse>> ask(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long conversationId,
            @Valid @RequestBody QuestionRequest request) {
        return ResponseEntity.ok(ApiResponse.of(
                chatService.ask(principal.getId(), conversationId, request.content())));
    }
}
