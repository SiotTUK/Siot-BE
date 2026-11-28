package com.siot.backend.checklist.controller;

import com.siot.backend.auth.security.MemberPrincipal;
import com.siot.backend.checklist.dto.GuideInfoResponse;
import com.siot.backend.checklist.dto.GuideInfoUpdateRequest;
import com.siot.backend.checklist.dto.GuideSummaryResponse;
import com.siot.backend.checklist.service.AdminGuideService;
import com.siot.backend.global.common.ApiResponse;
import com.siot.backend.global.config.openapi.ApiErrorCodes;
import com.siot.backend.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 관리자: 항목 목록과 항목의 언어와 상관없는 값 (출처 링크·문의 전화) */
@Tag(name = "9-3. 관리자: 항목 문구", description = "체크리스트·생활 변화 할 일·생활수칙의 언어별 상세 문구 (CHK-06, NTF-07). 관리자만")
@RestController
@RequestMapping("/api/admin/guides")
@RequiredArgsConstructor
public class AdminGuideInfoController {

    private final AdminGuideService adminGuideService;

    @Operation(summary = "CHK-06 항목 목록",
            description = "문구를 고칠 항목을 고를 수 있게 체크리스트·생활 변화 할 일·생활수칙의 코드와 한국어 제목을 준다. "
                    + "삭제한 항목은 빼고, 종류(체크리스트 → 생활 변화 할 일 → 생활수칙) 다음 정렬 순서·코드 순이다. "
                    + "kind 로 한 종류만 볼 수 있다.")
    @ApiErrorCodes(ErrorCode.INVALID_INPUT)
    @GetMapping
    public ResponseEntity<ApiResponse<List<GuideSummaryResponse>>> list(
            @Parameter(description = "CHECKLIST / LIFE_CHANGE_TASK / RULE. 생략 시 전체") @RequestParam(required = false) String kind) {
        return ResponseEntity.ok(ApiResponse.of(adminGuideService.list(kind)));
    }

    @Operation(summary = "CHK-06·NTF-07 항목 출처 링크·문의 전화 조회",
            description = "체크리스트·생활 변화 할 일·생활수칙의 출처 링크(source_url)와 문의 전화(contact)를 읽는다. "
                    + "수정 화면이 지금 값을 채워 보여 줄 때 쓴다. 언어와 상관없는 항목 공통 값이다.")
    @ApiErrorCodes(ErrorCode.GUIDE_NOT_FOUND)
    @GetMapping("/{code}")
    public ResponseEntity<ApiResponse<GuideInfoResponse>> getInfo(
            @Parameter(description = "항목 ID (CL-01, LC-01-1, RUL-01 …)") @PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.of(adminGuideService.getInfo(code)));
    }

    @Operation(summary = "CHK-06·NTF-07 항목 출처 링크·문의 전화 수정",
            description = "체크리스트·생활 변화 할 일·생활수칙의 출처 링크(source_url)와 문의 전화(contact)를 바꾼다. "
                    + "보내지 않은 값은 그대로, 빈 문자열은 지운다. 출처 링크는 http(s):// 로 시작해야 한다. "
                    + "언어별 문구는 PUT /api/admin/guides/{code}/translations/{language} 로 저장한다.")
    @ApiErrorCodes(ErrorCode.GUIDE_NOT_FOUND)
    @PatchMapping("/{code}")
    public ResponseEntity<ApiResponse<GuideInfoResponse>> updateInfo(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Parameter(description = "항목 ID (CL-01, LC-01-1, RUL-01 …)") @PathVariable String code,
            @Valid @RequestBody GuideInfoUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.of(adminGuideService.updateInfo(principal.getId(), code, request)));
    }
}
