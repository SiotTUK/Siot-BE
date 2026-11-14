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
import com.siot.backend.global.config.openapi.ApiErrorCodes;
import com.siot.backend.global.config.openapi.SuccessStatus;
import com.siot.backend.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/** NTF-09: 가입한 뒤의 이메일 인증 (인증 기능 이전에 가입한 미인증 회원용) */
@Tag(name = "3. 회원")
@RestController
@RequestMapping("/api/members/me/email")
@RequiredArgsConstructor
public class MemberEmailController {

    private final EmailVerificationService emailVerificationService;

    /** 가입 이메일로 인증 코드 발송 (202) */
    @Operation(summary = "NTF-09 이메일 인증 코드 발송 (기존 미인증 회원)",
            description = "새 회원은 가입 때 인증하므로 쓰지 않는다. emailVerified=false 인 회원용. "
                    + "인증해야 비밀번호 재설정과 이메일 알림을 받는다. 코드 10분 유효, 1분 1회·하루 10회.")
    @SuccessStatus(value = 202, description = "발송 요청 접수")
    @ApiErrorCodes({ErrorCode.EMAIL_ALREADY_VERIFIED, ErrorCode.EMAIL_VERIFICATION_TOO_FREQUENT,
            ErrorCode.EMAIL_VERIFICATION_DAILY_LIMIT})
    @PostMapping("/verification")
    public ResponseEntity<ApiResponse<EmailVerificationResponse>> requestVerification(
            @AuthenticationPrincipal MemberPrincipal principal) {
        EmailVerificationResponse response = emailVerificationService.requestVerification(principal.getId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.of(response));
    }

    /** 인증 코드 확인 → 인증 완료 */
    @Operation(summary = "NTF-09 이메일 인증 코드 확인",
            description = "성공하면 emailVerified=true 인 내 정보를 돌려준다. 5회 틀리거나 10분이 지나면 코드를 다시 요청한다.")
    @ApiErrorCodes({ErrorCode.EMAIL_VERIFICATION_CODE_INVALID, ErrorCode.EMAIL_VERIFICATION_EXPIRED})
    @PostMapping("/verification/confirm")
    public ResponseEntity<ApiResponse<MemberResponse>> confirm(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody EmailVerificationConfirmRequest request) {
        return ResponseEntity.ok(ApiResponse.of(emailVerificationService.verify(principal.getId(), request.code())));
    }
}
