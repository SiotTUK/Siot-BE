package com.siot.backend.auth.controller;

import com.siot.backend.auth.dto.EmailAvailabilityResponse;
import com.siot.backend.auth.domain.CredentialPolicy;
import com.siot.backend.auth.dto.LoginRequest;
import com.siot.backend.auth.dto.SignupRequest;
import com.siot.backend.auth.dto.SignupVerificationConfirmRequest;
import com.siot.backend.auth.dto.SignupVerificationRequest;
import com.siot.backend.auth.dto.SignupVerificationResponse;
import com.siot.backend.auth.service.SignupVerificationService;
import com.siot.backend.auth.service.AuthService;
import com.siot.backend.global.common.ApiResponse;
import com.siot.backend.member.dto.MemberResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SignupVerificationService signupVerificationService;

    /** M-02: 이메일(아이디) 중복 확인 */
    @GetMapping("/email/availability")
    public ResponseEntity<ApiResponse<EmailAvailabilityResponse>> checkEmail(@RequestParam String email) {
        String normalized = CredentialPolicy.normalizeEmail(email);
        boolean available = authService.isEmailAvailable(normalized);
        return ResponseEntity.ok(ApiResponse.of(new EmailAvailabilityResponse(normalized, available)));
    }

    /** M-01: 가입 인증 코드 발송 (202) */
    @PostMapping("/signup/email-verification")
    public ResponseEntity<ApiResponse<SignupVerificationResponse>> requestSignupCode(
            @Valid @RequestBody SignupVerificationRequest request) {
        SignupVerificationResponse response = signupVerificationService.requestCode(request.email(), request.language());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.of(response));
    }

    /** M-01: 가입 인증 코드 확인 (204). 확인 후 30분 안에 가입해야 한다 */
    @PostMapping("/signup/email-verification/confirm")
    public ResponseEntity<Void> confirmSignupCode(@Valid @RequestBody SignupVerificationConfirmRequest request) {
        signupVerificationService.confirmCode(request.email(), request.code());
        return ResponseEntity.noContent().build();
    }

    /** M-01: 회원가입. 인증 코드를 확인한 이메일만 가능 (가입 후 로그인 상태) */
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<MemberResponse>> signup(@Valid @RequestBody SignupRequest request,
                                                              HttpServletRequest httpRequest,
                                                              HttpServletResponse httpResponse) {
        MemberResponse member = authService.signup(request, httpRequest, httpResponse);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(member));
    }

    /** M-08: 로그인 */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<MemberResponse>> login(@Valid @RequestBody LoginRequest request,
                                                             HttpServletRequest httpRequest,
                                                             HttpServletResponse httpResponse) {
        return ResponseEntity.ok(ApiResponse.of(authService.login(request, httpRequest, httpResponse)));
    }

    /** M-09: 로그아웃 */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        authService.logout(httpRequest, httpResponse);
        return ResponseEntity.noContent().build();
    }
}
