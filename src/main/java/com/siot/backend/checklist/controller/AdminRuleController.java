package com.siot.backend.checklist.controller;

import com.siot.backend.auth.security.MemberPrincipal;
import com.siot.backend.checklist.dto.RuleCreateRequest;
import com.siot.backend.checklist.dto.RuleResponse;
import com.siot.backend.checklist.dto.RuleUpdateRequest;
import com.siot.backend.checklist.service.AdminRuleService;
import com.siot.backend.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "관리자 - 생활수칙 관리", description = "생활수칙 등록·수정·소프트 삭제 및 다국어 자동 번역 (RUL-09~11, ADM-06, RUL-13)")
@RestController
@RequestMapping("/api/admin/rules")
@RequiredArgsConstructor
public class AdminRuleController {

    private final AdminRuleService adminRuleService;

    @Operation(summary = "RUL-09·ADM-06 생활수칙 등록",
            description = "새로운 생활수칙을 등록합니다. 등록 시 다국어(영어, 중국어, 베트남어) 번역본이 자동 생성됩니다 (RUL-13).")
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
