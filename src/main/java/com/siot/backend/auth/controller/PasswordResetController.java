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

/** M-11: 비밀번호 재설정 */
@RestController
@RequestMapping("/api/auth/password-reset")
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    /**
     * 재설정 링크 요청. 가입·인증 여부와 관계없이 같은 202 응답을 준다.
     * 화면 문구: "가입·인증된 이메일이면 메일을 보냈습니다"
     */
    @PostMapping
    public ResponseEntity<Void> request(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.requestReset(request.email());
        return ResponseEntity.accepted().build();
    }

    /** 재설정 화면 진입 시 링크 유효성 확인 (유효 204 / 만료·사용됨 400) */
    @GetMapping("/validation")
    public ResponseEntity<Void> validate(@RequestParam String token) {
        passwordResetService.validateToken(token);
        return ResponseEntity.noContent().build();
    }

    /** 새 비밀번호 설정. 성공하면 그 회원의 모든 세션이 끊긴다. */
    @PostMapping("/confirm")
    public ResponseEntity<Void> confirm(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirmReset(request.token(), request.password(), request.passwordConfirm());
        return ResponseEntity.noContent().build();
    }
}
