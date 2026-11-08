package com.siot.backend.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CSRF 토큰 발급.
 * 호출하면 응답에 XSRF-TOKEN 쿠키가 설정된다. 클라이언트는 이 쿠키 값을
 * POST/PUT/PATCH/DELETE 요청의 X-XSRF-TOKEN 헤더로 보낸다. (docs/conventions.md 참고)
 */
@RestController
@RequestMapping("/api/auth")
public class CsrfController {

    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(CsrfToken csrfToken) {
        // 지연 로딩되는 토큰을 읽어서 XSRF-TOKEN 쿠키가 응답에 실리도록 한다.
        csrfToken.getToken();
        return ResponseEntity.noContent().build();
    }
}
