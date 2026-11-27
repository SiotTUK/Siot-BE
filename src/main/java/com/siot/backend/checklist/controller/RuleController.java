package com.siot.backend.checklist.controller;

import com.siot.backend.checklist.dto.RuleCategoryResponse;
import com.siot.backend.checklist.dto.RuleResponse;
import com.siot.backend.checklist.service.RuleService;
import com.siot.backend.global.common.ApiResponse;
import com.siot.backend.global.common.SupportedLanguages;
import com.siot.backend.global.config.openapi.ApiErrorCodes;
import com.siot.backend.global.exception.ErrorCode;
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
@Tag(name = "6. 생활수칙", description = "기초생활수칙, 쓰레기 배출, 교통질서, 세금, FAQ TOP 5 (RUL-01~13)")
@RestController
@RequestMapping("/api/rules")
@RequiredArgsConstructor
public class RuleController {

    private final RuleService ruleService;

    @Operation(summary = "RUL-04 자주 묻는 질문",
            description = "질문·답변(요청 언어, 없으면 한국어)과 자세히 볼 생활수칙 코드(ruleCode), 순서대로. "
                    + "#14 이전에는 is_faq 생활수칙 목록을 돌려주었다.")
    @ApiErrorCodes({ErrorCode.INVALID_INPUT})
    @GetMapping("/faq")
    public ResponseEntity<ApiResponse<List<com.siot.backend.checklist.dto.RuleFaqResponse>>> getFaqs(
            @Parameter(description = "언어 (ko/en/zh/vi). 생략 시 ko, 그 밖의 값은 400") @RequestParam(required = false) String language) {
        return ResponseEntity.ok(ApiResponse.of(ruleService.getFaqs(SupportedLanguages.requireValid(language))));
    }

    @Operation(summary = "카테고리별 생활수칙 목록 조회",
            description = "카테고리를 지정하지 않거나 '전체'면 전체 생활수칙을 카테고리 순서·정렬순으로 반환합니다. "
                    + "본문은 points(문장 목록), 시기는 when, 출처는 sources.")
    @ApiErrorCodes({ErrorCode.INVALID_INPUT})
    @GetMapping
    public ResponseEntity<ApiResponse<List<RuleResponse>>> getRules(
            @Parameter(description = "분류: 한국어 이름(쓰레기 등) 또는 FE 키(order, waste, traffic, tax, living, support)")
            @RequestParam(required = false) String category,
            @Parameter(description = "언어 (ko/en/zh/vi). 생략 시 ko, 그 밖의 값은 400") @RequestParam(required = false) String language) {

        List<RuleResponse> list = ruleService.getRules(category, SupportedLanguages.requireValid(language));
        return ResponseEntity.ok(ApiResponse.of(list));
    }

    @Operation(summary = "RUL-12·RUL-13 생활수칙 카테고리 목록 조회",
            description = "등록된 생활수칙의 카테고리를 중복 없이 조회합니다. category 는 목록 필터 값(한국어 원문), "
                    + "name 은 요청 언어의 이름입니다. 번역이 없으면 한국어 원문과 translated=false (COM-02 '번역 준비 중').")
    @ApiErrorCodes({ErrorCode.INVALID_INPUT})
    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<RuleCategoryResponse>>> getCategories(
            @Parameter(description = "언어 (ko/en/zh/vi). 생략 시 ko, 그 밖의 값은 400") @RequestParam(required = false) String language) {
        return ResponseEntity.ok(ApiResponse.of(ruleService.getCategories(SupportedLanguages.requireValid(language))));
    }

    @Operation(summary = "생활수칙 단건 상세 조회")
    @ApiErrorCodes({ErrorCode.GUIDE_NOT_FOUND, ErrorCode.INVALID_INPUT})
    @GetMapping("/{code}")
    public ResponseEntity<ApiResponse<RuleResponse>> getRule(
            @Parameter(description = "항목 코드 (FE 슬러그, 예: waste-food)") @PathVariable String code,
            @Parameter(description = "언어 (ko/en/zh/vi). 생략 시 ko, 그 밖의 값은 400") @RequestParam(required = false) String language) {

        RuleResponse rule = ruleService.getRule(code, SupportedLanguages.requireValid(language));
        return ResponseEntity.ok(ApiResponse.of(rule));
    }
}
