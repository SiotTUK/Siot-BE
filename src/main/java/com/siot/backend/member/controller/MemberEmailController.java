package com.siot.backend.member.controller;

import com.siot.backend.auth.security.MemberPrincipal;
import com.siot.backend.global.common.ApiResponse;
import com.siot.backend.member.dto.EmailVerificationConfirmRequest;
import com.siot.backend.member.dto.EmailVerificationRequest;
import com.siot.backend.member.dto.EmailVerificationResponse;
import com.siot.backend.member.dto.MemberResponse;
import com.siot.backend.member.service.EmailVerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** NTF-09: 알림 이메일 등록·인증·해제 */
@RestController
@RequestMapping("/api/members/me/email")
@RequiredArgsConstructor
public class MemberEmailController {

    private final EmailVerificationService emailVerificationService;

    /** 인증 코드 발송 (202) */
    @PostMapping("/verification")
    public ResponseEntity<ApiResponse<EmailVerificationResponse>> requestVerification(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody EmailVerificationRequest request) {
        EmailVerificationResponse response =
                emailVerificationService.requestVerification(principal.getId(), request.email());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.of(response));
    }

    /** 인증 코드 확인 → 알림 이메일 등록 */
    @PostMapping("/verification/confirm")
    public ResponseEntity<ApiResponse<MemberResponse>> confirm(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody EmailVerificationConfirmRequest request) {
        return ResponseEntity.ok(ApiResponse.of(emailVerificationService.verify(principal.getId(), request.code())));
    }

    /** 알림 이메일 등록 해제 */
    @DeleteMapping
    public ResponseEntity<ApiResponse<MemberResponse>> remove(@AuthenticationPrincipal MemberPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.of(emailVerificationService.remove(principal.getId())));
    }
}
