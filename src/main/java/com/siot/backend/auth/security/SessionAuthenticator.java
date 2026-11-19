package com.siot.backend.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;

/**
 * 세션 기반 로그인·로그아웃 처리 (M-08, M-09).
 * 인증 결과를 Spring Session(Redis) 세션에 저장한다.
 */
@Component
@RequiredArgsConstructor
public class SessionAuthenticator {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final CsrfTokenRepository csrfTokenRepository;
    private final SecurityContextHolderStrategy contextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    /**
     * 이메일·비밀번호를 검증하고 세션에 로그인 상태를 저장한다.
     *
     * @throws AuthenticationException 가입하지 않은 이메일이거나 비밀번호가 틀린 경우
     */
    public MemberPrincipal login(String email, String password,
                                 HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(email, password));
        saveToSession(authentication, request, response);
        return (MemberPrincipal) authentication.getPrincipal();
    }

    /** 이미 검증된 회원(가입 직후 등)을 로그인 상태로 만든다. */
    public void login(MemberPrincipal principal, HttpServletRequest request, HttpServletResponse response) {
        principal.eraseCredentials();
        saveToSession(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()), request, response);
    }

    /** M-09: 서버 세션 삭제 */
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        logoutHandler.logout(request, response, contextHolderStrategy.getContext().getAuthentication());
        rotateCsrfToken(request, response);
    }

    /**
     * 로그인 상태가 바뀌면 CSRF 토큰을 새로 발급한다 (Spring 기본 로그인의 CsrfAuthenticationStrategy 와 같은 목적:
     * 로그인 전에 노출되거나 심어진 토큰을 로그인 후에 쓰지 못하게). 응답에 새 XSRF-TOKEN 쿠키가 실린다.
     * 지우기만 하면 프론트엔드가 다시 /api/auth/csrf 를 불러야 하므로 바로 새 값을 준다.
     */
    private void rotateCsrfToken(HttpServletRequest request, HttpServletResponse response) {
        CsrfToken token = csrfTokenRepository.generateToken(request);
        csrfTokenRepository.saveToken(token, request, response);
    }

    private void saveToSession(Authentication authentication,
                               HttpServletRequest request, HttpServletResponse response) {
        // 세션 고정 공격 방지: 로그인 전 세션(CSRF 토큰 등)이 있으면 세션 ID를 바꾼다
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        SecurityContext context = contextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        rotateCsrfToken(request, response);
    }
}
