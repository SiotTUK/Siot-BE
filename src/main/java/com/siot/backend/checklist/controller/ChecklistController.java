package com.siot.backend.checklist.controller;

import com.siot.backend.auth.security.MemberPrincipal;
import com.siot.backend.checklist.dto.*;
import com.siot.backend.checklist.service.ChecklistService;
import com.siot.backend.checklist.service.MemberLifeChangeService;
import com.siot.backend.global.common.ApiResponse;
import com.siot.backend.global.config.openapi.ApiErrorCodes;
import com.siot.backend.global.config.openapi.SuccessStatus;
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

/**
 * 회원 맞춤 정착 체크리스트 & 생활 변화 등록 REST API (CHK-01 ~ CHK-12)
 */
@Tag(name = "5. 체크리스트", description = "맞춤 체크리스트 조회, 완료/해당없음 상태 변경, 생활 변화 등록/삭제 (CHK-01~12)")
@RestController
@RequestMapping("/api/members/me")
@RequiredArgsConstructor
public class ChecklistController {

    private final ChecklistService checklistService;
    private final MemberLifeChangeService memberLifeChangeService;

    @Operation(summary = "내 맞춤 체크리스트 및 진행률 조회",
            description = "온보딩 답과 등록된 생활 변화를 바탕으로 마감일·D-day를 실시간 계산하여 반환합니다.")
    @ApiErrorCodes({ErrorCode.MEMBER_NOT_FOUND})
    @GetMapping("/checklist")
    public ResponseEntity<ApiResponse<ChecklistSummaryResponse>> getMyChecklist(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Parameter(description = "화면 언어 (ko/en/zh/vi). 생략 시 회원 기본 언어")
            @RequestParam(required = false) String language) {

        ChecklistSummaryResponse response = checklistService.getMyChecklist(principal.getId(), language);
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @Operation(summary = "체크리스트 항목 상태 변경 (완료 / 해당 없음 / 해제)",
            description = "status에 DONE 또는 NOT_APPLICABLE을 지정합니다. status를 null로 보내면 상태가 해제(TODO)됩니다.")
    @SuccessStatus(value = 204, description = "상태 변경 완료")
    @ApiErrorCodes({ErrorCode.GUIDE_NOT_FOUND})
    @PutMapping("/checklist/{code}")
    public ResponseEntity<Void> updateProgress(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Parameter(description = "항목 코드 (CL-01, LC-01-1 …)") @PathVariable String code,
            @RequestBody ChecklistProgressUpdateRequest request) {

        checklistService.updateProgress(principal.getId(), code, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "등록된 생활 변화 목록 조회", description = "회원이 등록한 생활 변화(이사, 이직 등)와 기준일 목록을 조회합니다.")
    @GetMapping("/life-changes")
    public ResponseEntity<ApiResponse<List<LifeChangeResponse>>> getLifeChanges(
            @AuthenticationPrincipal MemberPrincipal principal) {

        List<LifeChangeResponse> list = memberLifeChangeService.getLifeChanges(principal.getId());
        return ResponseEntity.ok(ApiResponse.of(list));
    }

    @Operation(summary = "생활 변화 등록 또는 기준일 수정",
            description = "동일한 생활 변화를 다시 등록하면 기존 항목의 기준일(baseDate)만 변경됩니다.")
    @ApiErrorCodes({ErrorCode.INVALID_LIFE_CHANGE_CODE})
    @PutMapping("/life-changes/{code}")
    public ResponseEntity<ApiResponse<LifeChangeResponse>> registerLifeChange(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Parameter(description = "생활 변화 코드 (LC-01 ~ LC-08)") @PathVariable String code,
            @Valid @RequestBody LifeChangeRequest request) {

        LifeChangeRequest req = new LifeChangeRequest(code, request.baseDate());
        LifeChangeResponse response = memberLifeChangeService.registerLifeChange(principal.getId(), req);
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @Operation(summary = "생활 변화 삭제", description = "생활 변화를 삭제하면 관련된 생활 변화 할 일도 체크리스트에서 제외됩니다.")
    @SuccessStatus(value = 204, description = "삭제 완료")
    @ApiErrorCodes({ErrorCode.INVALID_LIFE_CHANGE_CODE, ErrorCode.LIFE_CHANGE_NOT_FOUND})
    @DeleteMapping("/life-changes/{code}")
    public ResponseEntity<Void> deleteLifeChange(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Parameter(description = "생활 변화 코드 (LC-01 ~ LC-08)") @PathVariable String code) {

        memberLifeChangeService.deleteLifeChange(principal.getId(), code);
        return ResponseEntity.noContent().build();
    }
}
