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

/** ADM-01: 회원 권한 관리 (관리자 전용, SecurityConfig 의 /api/admin/**) */
@RestController
@RequestMapping("/api/admin/members")
@RequiredArgsConstructor
public class AdminMemberController {

    private final AdminMemberService adminMemberService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminMemberResponse>>> search(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) Role role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return ResponseEntity.ok(ApiResponse.of(PageResponse.from(adminMemberService.search(email, role, pageable))));
    }

    /** 권한 변경. 대상 회원의 기존 세션은 모두 끊긴다 */
    @PutMapping("/{memberId}/role")
    public ResponseEntity<ApiResponse<AdminMemberResponse>> changeRole(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long memberId,
            @Valid @RequestBody RoleChangeRequest request) {
        return ResponseEntity.ok(ApiResponse.of(
                adminMemberService.changeRole(principal.getId(), memberId, request.role())));
    }
}
