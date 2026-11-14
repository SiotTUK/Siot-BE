package com.siot.backend.auth.controller;

import com.siot.backend.auth.dto.PasswordResetConfirmRequest;
import com.siot.backend.auth.dto.PasswordResetRequest;
import com.siot.backend.auth.service.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

/** M-11: 비밀번호 재설정 */
@Tag(name = "2. 비밀번호 재설정", description = "가입한 이메일로 재설정 링크 받기 (M-11). 로그인 없이 사용")
@RestController
@RequestMapping("/api/auth/password-reset")
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    /**
     * 재설정 링크 요청. 가입·인증 여부와 관계없이 같은 202 응답을 준다.
     * 화면 문구: "가입·인증된 이메일이면 메일을 보냈습니다"
     */
    @Operation(summary = "M-11 재설정 링크 요청",
            description = "가입·인증 여부와 관계없이 항상 202 (화면: \"가입·인증된 이메일이면 메일을 보냈습니다\"). "
                    + "메일 링크는 {FRONTEND_BASE_URL}/password-reset?token=... 이고 30분 동안 한 번만 쓸 수 있다. "
                    + "이메일당 1분 1회·하루 5회.")
    @SuccessStatus(value = 202, description = "요청 접수 (메일 발송 여부는 알려 주지 않음)")
    @ApiErrorCodes({ErrorCode.PASSWORD_RESET_TOO_FREQUENT, ErrorCode.PASSWORD_RESET_DAILY_LIMIT})
    @PostMapping
    public ResponseEntity<Void> request(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.requestReset(request.email());
        return ResponseEntity.accepted().build();
    }

    /** 재설정 화면 진입 시 링크 유효성 확인 (유효 204 / 만료·사용됨 400) */
    @Operation(summary = "M-11 재설정 링크 확인", description = "재설정 화면에 들어올 때 링크가 아직 유효한지 확인한다 (선택).")
    @SuccessStatus(value = 204, description = "유효한 링크")
    @ApiErrorCodes(ErrorCode.PASSWORD_RESET_TOKEN_INVALID)
    @GetMapping("/validation")
    public ResponseEntity<Void> validate(@RequestParam String token) {
        passwordResetService.validateToken(token);
        return ResponseEntity.noContent().build();
    }

    /** 새 비밀번호 설정. 성공하면 그 회원의 모든 세션이 끊긴다. */
    @Operation(summary = "M-11 새 비밀번호 설정",
            description = "비밀번호 규칙은 회원가입과 같다. 규칙 위반으로 실패하면 링크는 소모되지 않는다. "
                    + "성공하면 그 회원의 모든 세션이 끊기고 로그인 잠금도 풀린다.")
    @SuccessStatus(value = 204, description = "변경 완료")
    @ApiErrorCodes({ErrorCode.PASSWORD_RESET_TOKEN_INVALID, ErrorCode.PASSWORD_LENGTH, ErrorCode.PASSWORD_WHITESPACE,
            ErrorCode.PASSWORD_INVALID_CHARACTER, ErrorCode.PASSWORD_LETTER_AND_DIGIT_REQUIRED,
            ErrorCode.PASSWORD_SAME_AS_EMAIL, ErrorCode.PASSWORD_CONFIRM_MISMATCH})
    @PostMapping("/confirm")
    public ResponseEntity<Void> confirm(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirmReset(request.token(), request.password(), request.passwordConfirm());
        return ResponseEntity.noContent().build();
    }
}
