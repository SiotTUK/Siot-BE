package com.siot.backend.checklist.controller;

import com.siot.backend.checklist.dto.RuleResponse;
import com.siot.backend.checklist.service.RuleService;
import com.siot.backend.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 생활수칙 및 자주 묻는 질문(FAQ) REST API (RUL-01 ~ RUL-13)
 */
@Tag(name = "5. 생활수칙", description = "기초생활수칙, 쓰레기 배출, 교통질서, 세금, FAQ TOP 5 (RUL-01~13)")
@RestController
@RequestMapping("/api/rules")
@RequiredArgsConstructor
public class RuleController {

    private final RuleService ruleService;

    @Operation(summary = "RUL-04 자주 묻는 질문 FAQ TOP 5 조회", description = "is_faq=true 인 생활수칙 상위 5개를 정렬순으로 조회합니다.")
    @GetMapping("/faq")
    public ResponseEntity<ApiResponse<List<RuleResponse>>> getFaqTop5(
            @Parameter(description = "언어 (ko/en/zh/vi). 생략 시 ko") @RequestParam(required = false) String language) {

        List<RuleResponse> faqList = ruleService.getFaqTop5(language);
        return ResponseEntity.ok(ApiResponse.of(faqList));
    }

    @Operation(summary = "카테고리별 생활수칙 목록 조회", description = "카테고리를 지정하지 않거나 '전체'면 전체 생활수칙을 정렬순으로 반환합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<RuleResponse>>> getRules(
            @Parameter(description = "분류 (쓰레기 배출, 교통질서 등)") @RequestParam(required = false) String category,
            @Parameter(description = "언어 (ko/en/zh/vi). 생략 시 ko") @RequestParam(required = false) String language) {

        List<RuleResponse> list = ruleService.getRules(category, language);
        return ResponseEntity.ok(ApiResponse.of(list));
    }

    @Operation(summary = "생활수칙 카테고리 목록 조회", description = "등록된 생활수칙의 카테고리 목록을 중복 없이 조회합니다.")
    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<String>>> getCategories() {
        return ResponseEntity.ok(ApiResponse.of(ruleService.getCategories()));
    }

    @Operation(summary = "생활수칙 단건 상세 조회")
    @GetMapping("/{code}")
    public ResponseEntity<ApiResponse<RuleResponse>> getRule(
            @Parameter(description = "항목 코드 (RUL-01 등)") @PathVariable String code,
            @Parameter(description = "언어 (ko/en/zh/vi). 생략 시 ko") @RequestParam(required = false) String language) {

        RuleResponse rule = ruleService.getRule(code, language);
        return ResponseEntity.ok(ApiResponse.of(rule));
    }
}
