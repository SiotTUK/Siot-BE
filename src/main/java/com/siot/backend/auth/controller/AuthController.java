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
import com.siot.backend.global.config.openapi.ApiErrorCodes;
import com.siot.backend.global.config.openapi.SuccessStatus;
import com.siot.backend.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "1. 인증·가입", description = "회원가입(이메일 인증 코드), 로그인, 로그아웃. 로그인 없이 사용 (M-01~M-03, M-08, M-09)")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SignupVerificationService signupVerificationService;

    /** M-02: 이메일(아이디) 중복 확인 */
    @Operation(summary = "M-02 이메일 중복 확인",
            description = "이메일(아이디)을 쓸 수 있는지 확인한다. 대소문자를 구분하지 않으며 응답의 email 은 소문자로 바꾼 값이다. "
                    + "가입 요청 때 서버가 다시 검사한다.")
    @ApiErrorCodes(ErrorCode.EMAIL_FORMAT)
    @GetMapping("/email/availability")
    public ResponseEntity<ApiResponse<EmailAvailabilityResponse>> checkEmail(@RequestParam String email) {
        String normalized = CredentialPolicy.normalizeEmail(email);
        boolean available = authService.isEmailAvailable(normalized);
        return ResponseEntity.ok(ApiResponse.of(new EmailAvailabilityResponse(normalized, available)));
    }

    /** M-01: 가입 인증 코드 발송 (202) */
    @Operation(summary = "M-01 가입 인증 코드 발송",
            description = "이메일로 6자리 코드를 보낸다 (language: 메일 문구 언어 ko/en/zh/vi, 생략 시 ko). "
                    + "코드는 10분 유효하고 다시 요청하면 이전 코드는 무효. 이메일당 1분 1회·하루 10회.")
    @SuccessStatus(value = 202, description = "발송 요청 접수")
    @ApiErrorCodes({ErrorCode.EMAIL_FORMAT, ErrorCode.DUPLICATE_EMAIL,
            ErrorCode.EMAIL_VERIFICATION_TOO_FREQUENT, ErrorCode.EMAIL_VERIFICATION_DAILY_LIMIT})
    @PostMapping("/signup/email-verification")
    public ResponseEntity<ApiResponse<SignupVerificationResponse>> requestSignupCode(
            @Valid @RequestBody SignupVerificationRequest request) {
        SignupVerificationResponse response = signupVerificationService.requestCode(request.email(), request.language());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.of(response));
    }

    /** M-01: 가입 인증 코드 확인 (204). 확인 후 30분 안에 가입해야 한다 */
    @Operation(summary = "M-01 가입 인증 코드 확인",
            description = "맞으면 30분 안에 가입(POST /api/auth/signup)을 마쳐야 한다. 5회 틀리거나 만료되면 코드를 다시 요청한다.")
    @SuccessStatus(value = 204, description = "인증 완료")
    @ApiErrorCodes({ErrorCode.EMAIL_VERIFICATION_CODE_INVALID, ErrorCode.EMAIL_VERIFICATION_EXPIRED})
    @PostMapping("/signup/email-verification/confirm")
    public ResponseEntity<Void> confirmSignupCode(@Valid @RequestBody SignupVerificationConfirmRequest request) {
        signupVerificationService.confirmCode(request.email(), request.code());
        return ResponseEntity.noContent().build();
    }

    /** M-01: 회원가입. 인증 코드를 확인한 이메일만 가능 (가입 후 로그인 상태) */
    @Operation(summary = "M-01~M-03 회원가입",
            description = "인증 코드를 확인한 이메일만 가입할 수 있다 (확인 한 번에 가입 한 번). 성공하면 바로 로그인 상태가 되고 "
                    + "SIOT_SESSION 쿠키가 설정된다. 비밀번호 규칙 위반은 처음 어긴 규칙 하나만 돌려준다. "
                    + "onboarded=false 면 온보딩(사용자 유형) 화면으로 이동한다.")
    @SuccessStatus(value = 201, description = "가입 완료 (로그인 상태)")
    @ApiErrorCodes({ErrorCode.EMAIL_FORMAT, ErrorCode.PASSWORD_LENGTH, ErrorCode.PASSWORD_WHITESPACE,
            ErrorCode.PASSWORD_INVALID_CHARACTER, ErrorCode.PASSWORD_LETTER_AND_DIGIT_REQUIRED,
            ErrorCode.PASSWORD_SAME_AS_EMAIL, ErrorCode.PASSWORD_CONFIRM_MISMATCH, ErrorCode.EMAIL_NOT_VERIFIED,
            ErrorCode.DUPLICATE_EMAIL})
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<MemberResponse>> signup(@Valid @RequestBody SignupRequest request,
                                                              HttpServletRequest httpRequest,
                                                              HttpServletResponse httpResponse) {
        MemberResponse member = authService.signup(request, httpRequest, httpResponse);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(member));
    }

    /** M-08: 로그인 */
    @Operation(summary = "M-08 로그인",
            description = "성공하면 SIOT_SESSION 쿠키가 설정된다 (마지막 요청 후 14일 유지, M-10). "
                    + "가입하지 않은 이메일과 틀린 비밀번호는 같은 오류다. 5회 연속 실패하면 5분 동안 막힌다.")
    @ApiErrorCodes({ErrorCode.LOGIN_FAILED, ErrorCode.LOGIN_LOCKED})
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<MemberResponse>> login(@Valid @RequestBody LoginRequest request,
                                                             HttpServletRequest httpRequest,
                                                             HttpServletResponse httpResponse) {
        return ResponseEntity.ok(ApiResponse.of(authService.login(request, httpRequest, httpResponse)));
    }

    /** M-09: 로그아웃 */
    @Operation(summary = "M-09 로그아웃",
            description = "서버 세션을 삭제한다. 기기에 저장한 회원 정보 삭제는 프론트엔드에서 처리한다.")
    @SuccessStatus(value = 204, description = "로그아웃 완료")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        authService.logout(httpRequest, httpResponse);
        return ResponseEntity.noContent().build();
    }
}
