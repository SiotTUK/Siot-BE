package com.siot.backend.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.siot.backend.global.config.openapi.ApiErrorCodes;
import com.siot.backend.global.config.openapi.SuccessStatus;
import com.siot.backend.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * CSRF 토큰 발급.
 * 호출하면 응답에 XSRF-TOKEN 쿠키가 설정된다. 클라이언트는 이 쿠키 값을
 * POST/PUT/PATCH/DELETE 요청의 X-XSRF-TOKEN 헤더로 보낸다. (docs/conventions.md 참고)
 */
@Tag(name = "1. 인증·가입")
@RestController
@RequestMapping("/api/auth")
public class CsrfController {

    @Operation(summary = "CSRF 토큰 발급",
            description = "응답에 XSRF-TOKEN 쿠키가 설정된다. POST·PUT·PATCH·DELETE 는 이 값을 X-XSRF-TOKEN 헤더로 보낸다. "
                    + "앱 시작 시(이 화면에서는 맨 처음) 한 번 호출한다.")
    @SuccessStatus(value = 204, description = "XSRF-TOKEN 쿠키 발급")
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(@Parameter(hidden = true) CsrfToken csrfToken) {
        // 지연 로딩되는 토큰을 읽어서 XSRF-TOKEN 쿠키가 응답에 실리도록 한다.
        csrfToken.getToken();
        return ResponseEntity.noContent().build();
    }
}
