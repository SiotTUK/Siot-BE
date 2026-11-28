package com.siot.backend.checklist.controller;

import com.siot.backend.checklist.dto.AdminRuleFaqResponse;
import com.siot.backend.checklist.dto.RuleFaqRequest;
import com.siot.backend.checklist.service.AdminRuleFaqService;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "관리자 - 생활수칙 FAQ", description = "자주 묻는 질문 목록·등록·수정·삭제 (RUL-04, ADM-06). 질문·답변은 언어별로 직접 쓴다")
@RestController
@RequestMapping("/api/admin/rule-faqs")
@RequiredArgsConstructor
public class AdminRuleFaqController {

    private final AdminRuleFaqService adminRuleFaqService;

    @Operation(summary = "FAQ 목록",
            description = "관리자가 쓴 FAQ 를 순서대로 준다. 회원 화면(GET /api/rules/faq)은 이 목록 뒤에 "
                    + "FAQ 로 표시(faq=true)했지만 질문을 따로 쓰지 않은 생활수칙을 덧붙인다.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminRuleFaqResponse>>> getFaqs() {
        return ResponseEntity.ok(ApiResponse.of(adminRuleFaqService.getFaqs()));
    }

    @Operation(summary = "FAQ 등록",
            description = "ko 질문·답변은 필수. 연결할 생활수칙 코드가 없거나 지운 항목이면 404 GUIDE_NOT_FOUND.")
    @ApiErrorCodes(ErrorCode.GUIDE_NOT_FOUND)
    @PostMapping
    public ResponseEntity<ApiResponse<AdminRuleFaqResponse>> create(@Valid @RequestBody RuleFaqRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(adminRuleFaqService.create(request)));
    }

    @Operation(summary = "FAQ 수정", description = "보낸 값으로 통째로 바꾼다. 보내지 않은 언어의 질문·답변은 지운다.")
    @ApiErrorCodes({ErrorCode.RULE_FAQ_NOT_FOUND, ErrorCode.GUIDE_NOT_FOUND})
    @PutMapping("/{faqId}")
    public ResponseEntity<ApiResponse<AdminRuleFaqResponse>> update(
            @Parameter(description = "FAQ ID") @PathVariable Long faqId,
            @Valid @RequestBody RuleFaqRequest request) {
        return ResponseEntity.ok(ApiResponse.of(adminRuleFaqService.update(faqId, request)));
    }

    @Operation(summary = "FAQ 삭제", description = "질문·답변도 함께 지운다 (되돌릴 수 없다).")
    @ApiErrorCodes(ErrorCode.RULE_FAQ_NOT_FOUND)
    @DeleteMapping("/{faqId}")
    public ResponseEntity<Void> delete(@Parameter(description = "FAQ ID") @PathVariable Long faqId) {
        adminRuleFaqService.delete(faqId);
        return ResponseEntity.noContent().build();
    }
}
