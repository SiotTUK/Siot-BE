package com.siot.backend.checklist.controller;

import com.siot.backend.auth.security.MemberPrincipal;
import com.siot.backend.checklist.dto.GuideTranslationResponse;
import com.siot.backend.checklist.dto.GuideTranslationUpdateRequest;
import com.siot.backend.checklist.service.AdminGuideService;
import com.siot.backend.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.siot.backend.global.config.openapi.ApiErrorCodes;
import com.siot.backend.global.config.openapi.SuccessStatus;
import com.siot.backend.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 관리자: 항목 상세 문구 조회·저장 (CHK-06 필드, NTF-07 안내 내용 변경 기록) */
@Tag(name = "9-3. 관리자: 항목 문구", description = "체크리스트·생활 변화 할 일·생활수칙의 언어별 상세 문구 (CHK-06, NTF-07). 관리자만")
@RestController
@RequestMapping("/api/admin/guides/{code}/translations/{language}")
@RequiredArgsConstructor
public class AdminGuideController {

    private final AdminGuideService adminGuideService;

    @Operation(summary = "CHK-06 항목 문구 조회 (한 언어)")
    @ApiErrorCodes({ErrorCode.GUIDE_NOT_FOUND, ErrorCode.GUIDE_TRANSLATION_NOT_FOUND})
    @GetMapping
    public ResponseEntity<ApiResponse<GuideTranslationResponse>> get(
            @Parameter(description = "항목 ID (CL-01, LC-01-1 …)") @PathVariable String code,
            @Parameter(description = "ko / en / zh / vi") @PathVariable String language) {
        return ResponseEntity.ok(ApiResponse.of(adminGuideService.get(code, language)));
    }

    @Operation(summary = "CHK-06·NTF-07 항목 문구 저장",
            description = "보낸 값으로 전부 바꾼다 (빠진 필드·빈 문자열은 지움). 없던 언어면 새로 만든다. "
                    + "noticeChanged=true(실제 안내 내용 변경)면 notice_changed_at 을 지금으로 기록해 상세 화면에 '변경됨'이 표시되고, "
                    + "false(오타 수정 등)면 그대로 둔다. 어느 쪽이든 updated_at·updated_by 는 바뀐다.")
    @ApiErrorCodes(ErrorCode.GUIDE_NOT_FOUND)
    @PutMapping
    public ResponseEntity<ApiResponse<GuideTranslationResponse>> save(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Parameter(description = "항목 ID (CL-01, LC-01-1 …)") @PathVariable String code,
            @Parameter(description = "ko / en / zh / vi") @PathVariable String language,
            @Valid @RequestBody GuideTranslationUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.of(adminGuideService.save(principal.getId(), code, language, request)));
    }
}
