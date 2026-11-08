package com.siot.backend.auth.controller;

import com.siot.backend.auth.dto.LoginIdAvailabilityResponse;
import com.siot.backend.auth.dto.LoginRequest;
import com.siot.backend.auth.dto.SignupRequest;
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

    /** M-02: ID 중복 확인 */
    @GetMapping("/login-id/availability")
    public ResponseEntity<ApiResponse<LoginIdAvailabilityResponse>> checkLoginId(@RequestParam String loginId) {
        boolean available = authService.isLoginIdAvailable(loginId);
        return ResponseEntity.ok(ApiResponse.of(new LoginIdAvailabilityResponse(loginId, available)));
    }

    /** M-01: 회원가입 (가입 후 로그인 상태) */
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
