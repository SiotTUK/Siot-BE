package com.siot.backend.rag.controller;

import com.siot.backend.auth.security.MemberPrincipal;
import com.siot.backend.global.common.ApiResponse;
import com.siot.backend.global.common.PageResponse;
import com.siot.backend.rag.dto.AskResponse;
import com.siot.backend.rag.dto.ConversationCreateRequest;
import com.siot.backend.rag.dto.ConversationResponse;
import com.siot.backend.rag.dto.FeedbackRequest;
import com.siot.backend.rag.dto.SuggestionsResponse;
import com.siot.backend.rag.dto.MessageResponse;
import com.siot.backend.rag.dto.QuestionRequest;
import com.siot.backend.rag.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

import java.util.List;

/** 챗봇 (회원 전용). 대화는 본인 것만 조회·사용할 수 있다 (NFR-03). */
@Tag(name = "4. 챗봇", description = "대화, 질문·답변, 답변 평가, 추천 질문 (CB). 본인 대화만 볼 수 있다")
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /** CB-03: 새 대화 (201) */
    @Operation(summary = "CB-03 새 대화",
            description = "본문은 생략할 수 있다. language(ko/en/zh/vi)를 생략하면 회원의 화면 언어로 시작한다.")
    @SuccessStatus(value = 201, description = "대화 생성")
    @PostMapping("/conversations")
    public ResponseEntity<ApiResponse<ConversationResponse>> start(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody(required = false) ConversationCreateRequest request) {
        String language = request == null ? null : request.language();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(chatService.startConversation(principal.getId(), language)));
    }

    /** CB-03: 이전 대화 목록 (최근 순) */
    @Operation(summary = "CB-03 이전 대화 목록", description = "마지막 대화 시각 최신순. title 은 첫 질문.")
    @GetMapping("/conversations")
    public ResponseEntity<ApiResponse<PageResponse<ConversationResponse>>> list(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Parameter(description = "페이지 (0부터)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기 (최대 50)") @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
        return ResponseEntity.ok(ApiResponse.of(
                PageResponse.from(chatService.listConversations(principal.getId(), pageable))));
    }

    /** CB-03: 대화 내용 */
    @Operation(summary = "CB-03 대화 내용",
            description = "질문·답변을 오래된 순으로 돌려준다. 다른 회원의 대화는 존재 여부도 드러내지 않고 404 다.")
    @ApiErrorCodes(ErrorCode.CHAT_CONVERSATION_NOT_FOUND)
    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<ApiResponse<List<MessageResponse>>> messages(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long conversationId) {
        return ResponseEntity.ok(ApiResponse.of(chatService.getMessages(principal.getId(), conversationId)));
    }

    /** CB-03: 대화 삭제 (204) */
    @Operation(summary = "CB-03 대화 삭제",
            description = "대화와 그 안의 질문·답변·출처를 모두 지운다 (복구 불가). 다른 회원의 대화이거나 이미 지운 대화는 404 다.")
    @SuccessStatus(value = 204, description = "삭제 완료")
    @ApiErrorCodes(ErrorCode.CHAT_CONVERSATION_NOT_FOUND)
    @DeleteMapping("/conversations/{conversationId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long conversationId) {
        chatService.deleteConversation(principal.getId(), conversationId);
        return ResponseEntity.noContent().build();
    }

    /** CB-16: 질문 → 답변 */
    @Operation(summary = "CB-16 질문 → 답변",
            description = "질문(1~1000자)의 개인정보를 가려서 저장하고(masked=true), 자료를 검색해 답변한다. "
                    + "answerStatus: ANSWERED(출처 있음) / NO_EVIDENCE(자료 없음, 문의처 안내) / ERROR(AI 오류) — 모두 200. "
                    + "emergencyContacts(CB-09 119·112)와 referral(CB-11 담당 기관)이 있으면 답변 위에 표시한다.")
    @ApiErrorCodes({ErrorCode.CHAT_QUESTION_LENGTH, ErrorCode.CHAT_CONVERSATION_NOT_FOUND})
    @PostMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<ApiResponse<AskResponse>> ask(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long conversationId,
            @Valid @RequestBody QuestionRequest request) {
        return ResponseEntity.ok(ApiResponse.of(
                chatService.ask(principal.getId(), conversationId, request.content())));
    }

    /** CB-04: 답변 평가 (204). 다시 보내면 덮어쓴다 */
    @Operation(summary = "CB-04 답변 평가",
            description = "도움이 됐어요/안 됐어요와 의견. 다시 보내면 덮어쓴다. 챗봇 답변만 평가할 수 있다. "
                    + "'안 됐어요'는 관리자 검토 목록(ADM-08)에 올라간다.")
    @SuccessStatus(value = 204, description = "저장 완료")
    @ApiErrorCodes({ErrorCode.CHAT_CONVERSATION_NOT_FOUND, ErrorCode.CHAT_MESSAGE_NOT_FOUND,
            ErrorCode.CHAT_FEEDBACK_NOT_ALLOWED})
    @PutMapping("/conversations/{conversationId}/messages/{messageId}/feedback")
    public ResponseEntity<Void> feedback(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long conversationId,
            @PathVariable Long messageId,
            @Valid @RequestBody FeedbackRequest request) {
        chatService.recordFeedback(principal.getId(), conversationId, messageId, request.helpful(), request.comment());
        return ResponseEntity.noContent().build();
    }

    /** CB-08: 사용자 유형별 추천 질문 3개 */
    @Operation(summary = "CB-08 추천 질문 3개", description = "사용자 유형(NEW/LIVING)에 맞는 질문. 온보딩 전이면 공통 질문.")
    @ApiErrorCodes(ErrorCode.INVALID_INPUT)
    @GetMapping("/suggestions")
    public ResponseEntity<ApiResponse<SuggestionsResponse>> suggestions(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Parameter(description = "ko / en / zh / vi. 생략하면 회원의 화면 언어, 그 밖의 값은 400")
            @RequestParam(required = false) String language) {
        return ResponseEntity.ok(ApiResponse.of(chatService.suggestions(principal.getId(), language)));
    }
}
