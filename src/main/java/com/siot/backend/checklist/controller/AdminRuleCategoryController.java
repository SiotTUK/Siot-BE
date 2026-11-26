package com.siot.backend.checklist.controller;

import com.siot.backend.auth.security.MemberPrincipal;
import com.siot.backend.checklist.dto.AdminRuleCategoryResponse;
import com.siot.backend.checklist.dto.RuleCategoryRenameRequest;
import com.siot.backend.checklist.dto.RuleCategoryTranslationRequest;
import com.siot.backend.checklist.service.RuleCategoryService;
import com.siot.backend.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "관리자 - 생활수칙 카테고리", description = "카테고리 목록·이름 바꾸기·카테고리명 번역 수정 (RUL-12, RUL-13, ADM-06)")
@RestController
@RequestMapping("/api/admin/rule-categories")
@RequiredArgsConstructor
public class AdminRuleCategoryController {

    private final RuleCategoryService ruleCategoryService;

    @Operation(summary = "카테고리 목록",
            description = "생활수칙 카테고리(한국어 원문)별 생활수칙 수와 en/zh/vi 이름. "
                    + "카테고리는 생활수칙을 등록할 때 생기고, 그 카테고리의 생활수칙을 모두 지우면 목록에서 빠진다.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminRuleCategoryResponse>>> getCategories() {
        return ResponseEntity.ok(ApiResponse.of(ruleCategoryService.getAdminCategories()));
    }

    @Operation(summary = "카테고리 이름 바꾸기",
            description = "한국어 카테고리명을 바꾸고 그 카테고리의 생활수칙을 모두 옮긴다. 번역은 새 이름으로 다시 만든다. "
                    + "이미 있는 이름이면 그 카테고리로 합친다.")
    @PutMapping("/{category}")
    public ResponseEntity<ApiResponse<AdminRuleCategoryResponse>> rename(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Parameter(description = "지금 카테고리명 (한국어)") @PathVariable String category,
            @Valid @RequestBody RuleCategoryRenameRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.of(ruleCategoryService.rename(principal.getId(), category, request.name())));
    }

    @Operation(summary = "카테고리명 번역 고치기", description = "자동 번역한 카테고리명을 고친다. language 는 en, zh, vi")
    @PutMapping("/{category}/translations/{language}")
    public ResponseEntity<ApiResponse<AdminRuleCategoryResponse>> updateTranslation(
            @Parameter(description = "카테고리명 (한국어)") @PathVariable String category,
            @Parameter(description = "en, zh, vi") @PathVariable String language,
            @Valid @RequestBody RuleCategoryTranslationRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.of(
                ruleCategoryService.updateTranslation(category, language, request.name())));
    }
}
