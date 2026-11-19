package com.siot.backend.global.config;

import com.siot.backend.global.exception.ErrorCode;
import com.siot.backend.global.exception.ErrorResponseWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;

/**
 * 세션(Spring Session Redis) 기반 보안 설정.
 * <p>
 * URL 규칙은 docs/conventions.md 의 "API 경로와 접근 권한" 을 따른다.
 * 새 공개 API 가 필요하면 아래 목록에 추가하고 문서도 함께 갱신한다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** 운영 확인용 */
    private static final String[] INFRA_PUBLIC = {
            "/actuator/health/**",
            "/actuator/info",
            "/error"
    };

    /** API 문서 (Swagger UI). springdoc 이 꺼진 환경(운영 기본값)에서는 404 */
    private static final String[] API_DOCS = {
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs",
            "/v3/api-docs/**"
    };

    /**
     * 비로그인 사용자가 쓸 수 있는 API. 회원가입하지 않으면 아무것도 못 본다 (11/13 결정).
     * M-01~M-03 가입 인증 코드·가입·이메일 중복 확인, M-08 로그인, M-09 로그아웃, M-11 비밀번호 재설정, CSRF 토큰
     */
    private static final String[] PUBLIC_ANY_METHOD = {
            "/api/auth/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // SPA + 세션 쿠키: XSRF-TOKEN 쿠키 값을 X-XSRF-TOKEN 헤더로 보내야 한다 (GET 제외)
                // 저장소는 빈으로 공유해 로그인 시 토큰을 바꿀 때(SessionAuthenticator) 같은 설정을 쓴다
                .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                // 로그인·로그아웃은 /api/auth/** 컨트롤러에서 직접 처리한다 (SessionAuthenticator)
                .logout(AbstractHttpConfigurer::disable)
                .securityContext(context -> context.securityContextRepository(securityContextRepository()))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.changeSessionId()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(INFRA_PUBLIC).permitAll()
                        .requestMatchers(API_DOCS).permitAll()
                        // ADM-01: 관리 API·운영 지표는 관리자만
                        .requestMatchers("/api/admin/**", "/actuator/**").hasRole("ADMIN")
                        .requestMatchers(PUBLIC_ANY_METHOD).permitAll()
                        // 그 외 모든 API (체크리스트·홈·행사·챗봇·신고 등) 는 로그인 필요
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) ->
                                ErrorResponseWriter.write(response, ErrorCode.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, e) ->
                                ErrorResponseWriter.write(response, e instanceof CsrfException
                                        ? ErrorCode.CSRF_TOKEN_INVALID
                                        : ErrorCode.FORBIDDEN)));

        return http.build();
    }

    /**
     * CSRF 토큰 저장소 (XSRF-TOKEN 쿠키, JavaScript 에서 읽을 수 있음). csrf.spa() 의 기본값과 같다.
     * 로그인을 컨트롤러에서 직접 처리하므로 Spring 의 로그인 시 토큰 교체(CsrfAuthenticationStrategy)가
     * 일어나지 않아 SessionAuthenticator 가 이 저장소로 직접 바꾼다.
     */
    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // NFR-02: 단방향 해시 (기본 bcrypt)
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /** 로그인 상태를 HTTP 세션(Spring Session → Redis)에 저장 */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /** 이메일·비밀번호 검증. 가입하지 않은 이메일과 틀린 비밀번호를 구분하지 않는다 (M-08). */
    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
