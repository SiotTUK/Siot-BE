package com.siot.backend.member.controller;

import com.siot.backend.auth.security.MemberPrincipal;
import com.siot.backend.global.common.ApiResponse;
import com.siot.backend.global.common.PageResponse;
import com.siot.backend.member.domain.Role;
import com.siot.backend.member.dto.AdminMemberResponse;
import com.siot.backend.member.dto.RoleChangeRequest;
import com.siot.backend.member.service.AdminMemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

/** ADM-01: 회원 권한 관리 (관리자 전용, SecurityConfig 의 /api/admin/**) */
@Tag(name = "9-1. 관리자: 회원", description = "회원 검색, 권한 변경 (ADM-01). 관리자만")
@RestController
@RequestMapping("/api/admin/members")
@RequiredArgsConstructor
public class AdminMemberController {

    private final AdminMemberService adminMemberService;

    @Operation(summary = "ADM-01 회원 검색", description = "가입 최신순. size 는 1~100.")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminMemberResponse>>> search(
            @Parameter(description = "이메일 부분 일치 (대소문자 무시)") @RequestParam(required = false) String email,
            @Parameter(description = "권한") @RequestParam(required = false) Role role,
            @Parameter(description = "페이지 (0부터)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기 (최대 100)") @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return ResponseEntity.ok(ApiResponse.of(PageResponse.from(adminMemberService.search(email, role, pageable))));
    }

    /** 권한 변경. 대상 회원의 기존 세션은 모두 끊긴다 */
    @Operation(summary = "ADM-01 권한 변경",
            description = "대상 회원의 기존 세션은 모두 끊기고, 다시 로그인해야 새 권한이 적용된다. 자기 자신의 관리자 권한은 내릴 수 없다. "
                    + "SIOT_ADMIN_EMAILS 에 있는 회원은 앱이 다시 시작되면 관리자로 돌아간다.")
    @ApiErrorCodes({ErrorCode.ADMIN_SELF_DEMOTION, ErrorCode.MEMBER_NOT_FOUND})
    @PutMapping("/{memberId}/role")
    public ResponseEntity<ApiResponse<AdminMemberResponse>> changeRole(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long memberId,
            @Valid @RequestBody RoleChangeRequest request) {
        return ResponseEntity.ok(ApiResponse.of(
                adminMemberService.changeRole(principal.getId(), memberId, request.role())));
    }
}
