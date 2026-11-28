package com.siot.backend.checklist.controller;

import com.siot.backend.auth.security.MemberPrincipal;
import com.siot.backend.checklist.dto.AdminRuleSummaryResponse;
import com.siot.backend.checklist.dto.RuleCreateRequest;
import com.siot.backend.checklist.dto.RuleResponse;
import com.siot.backend.checklist.dto.RuleUpdateRequest;
import com.siot.backend.checklist.service.AdminRuleService;
import com.siot.backend.global.common.ApiResponse;
import com.siot.backend.global.config.openapi.ApiErrorCodes;
import com.siot.backend.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "관리자 - 생활수칙 관리", description = "생활수칙 등록·수정·소프트 삭제 및 다국어 자동 번역 (RUL-09~11, ADM-06, RUL-13)")
@RestController
@RequestMapping("/api/admin/rules")
@RequiredArgsConstructor
public class AdminRuleController {

    private final AdminRuleService adminRuleService;

    @Operation(summary = "ADM-06 생활수칙 목록 (관리자)",
            description = "생활수칙을 카테고리 → 정렬 순서 → 코드 순으로 줍니다. 한국어 제목, FAQ·삭제 여부, "
                    + "번역이 있는/없는 언어를 함께 줍니다. 기본은 삭제한 항목을 빼고, includeDeleted=true 면 넣습니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminRuleSummaryResponse>>> getRules(
            @Parameter(description = "카테고리 (한국어). 생략 시 전체") @RequestParam(required = false) String category,
            @Parameter(description = "코드 또는 한국어 제목 검색어") @RequestParam(required = false) String keyword,
            @Parameter(description = "삭제한 생활수칙도 넣을지") @RequestParam(defaultValue = "false") boolean includeDeleted
    ) {
        return ResponseEntity.ok(ApiResponse.of(adminRuleService.getRules(category, keyword, includeDeleted)));
    }

    @Operation(summary = "ADM-06 생활수칙 상세 (관리자)",
            description = "수정 폼을 채울 한국어 원문 전체(문장 목록·출처 목록·확인일·정렬 순서 등)를 준다. "
                    + "삭제한 생활수칙도 읽을 수 있다 (수정·삭제는 안 된다).")
    @ApiErrorCodes(ErrorCode.GUIDE_NOT_FOUND)
    @GetMapping("/{code}")
    public ResponseEntity<ApiResponse<RuleResponse>> getRule(
            @Parameter(description = "항목 코드 (RUL-01 등)") @PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.of(adminRuleService.getRule(code)));
    }

    @Operation(summary = "RUL-09·ADM-06 생활수칙 등록",
            description = "새로운 생활수칙을 등록합니다. 카테고리·제목·본문은 필수입니다. 등록 시 다국어(영어, 중국어, 베트남어) "
                    + "번역본이 자동 생성됩니다 (RUL-13). 이미 쓰고 있거나 삭제한 생활수칙이 쓰던 코드면 409 DUPLICATE_GUIDE_CODE.")
    @ApiErrorCodes({ErrorCode.DUPLICATE_GUIDE_CODE})
    @PostMapping
    public ResponseEntity<ApiResponse<RuleResponse>> createRule(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody RuleCreateRequest request
    ) {
        RuleResponse response = adminRuleService.createRule(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
    }

    @Operation(summary = "RUL-10·ADM-06 생활수칙 수정",
            description = "생활수칙을 수정합니다. noticeChanged=true 인 경우 중요 안내 내용 변경으로 기록되어 회원들에게 변경 알림이 발송됩니다 (NTF-07).")
    @PutMapping("/{code}")
    public ResponseEntity<ApiResponse<RuleResponse>> updateRule(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Parameter(description = "항목 코드 (RUL-01 등)") @PathVariable String code,
            @Valid @RequestBody RuleUpdateRequest request
    ) {
        RuleResponse response = adminRuleService.updateRule(principal.getId(), code, request);
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @Operation(summary = "RUL-11·ADM-06 생활수칙 소프트 삭제",
            description = "생활수칙을 소프트 삭제(deleted_at 기록)합니다. 삭제된 항목은 사용자 화면에서 노출되지 않습니다.")
    @DeleteMapping("/{code}")
    public ResponseEntity<Void> deleteRule(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Parameter(description = "항목 코드 (RUL-01 등)") @PathVariable String code
    ) {
        adminRuleService.deleteRule(principal.getId(), code);
        return ResponseEntity.noContent().build();
    }
}
