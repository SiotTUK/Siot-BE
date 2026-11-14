package com.siot.backend.member.controller;

import com.siot.backend.auth.security.MemberPrincipal;
import com.siot.backend.global.common.ApiResponse;
import com.siot.backend.member.dto.EmailVerificationConfirmRequest;
import com.siot.backend.member.dto.EmailVerificationResponse;
import com.siot.backend.member.dto.MemberResponse;
import com.siot.backend.member.service.EmailVerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** NTF-09: 가입한 뒤의 이메일 인증 (인증 기능 이전에 가입한 미인증 회원용) */
@RestController
@RequestMapping("/api/members/me/email")
@RequiredArgsConstructor
public class MemberEmailController {

    private final EmailVerificationService emailVerificationService;

    /** 가입 이메일로 인증 코드 발송 (202) */
    @PostMapping("/verification")
    public ResponseEntity<ApiResponse<EmailVerificationResponse>> requestVerification(
            @AuthenticationPrincipal MemberPrincipal principal) {
        EmailVerificationResponse response = emailVerificationService.requestVerification(principal.getId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.of(response));
    }

    /** 인증 코드 확인 → 인증 완료 */
    @PostMapping("/verification/confirm")
    public ResponseEntity<ApiResponse<MemberResponse>> confirm(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody EmailVerificationConfirmRequest request) {
        return ResponseEntity.ok(ApiResponse.of(emailVerificationService.verify(principal.getId(), request.code())));
    }
}
