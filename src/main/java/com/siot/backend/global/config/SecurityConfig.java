package com.siot.backend.global.config;

import com.siot.backend.global.exception.ErrorCode;
import com.siot.backend.global.exception.ErrorResponseWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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

    /** 비로그인 사용자도 사용하는 API (모든 HTTP 메서드) */
    private static final String[] PUBLIC_ANY_METHOD = {
            "/api/auth/**",     // M-01~M-03 가입·ID 중복 확인, M-08 로그인, M-11 비밀번호 재설정, CSRF 토큰
            "/api/chat/**"      // CB-03 비회원도 이번 방문 대화 유지 (guest_session_id)
    };

    /** 비로그인 사용자도 조회할 수 있는 콘텐츠 (GET 만 허용) */
    private static final String[] PUBLIC_GET = {
            "/api/checklists/**",   // CHK 체크리스트 항목·상세 (개인 진행 상태는 /api/members/me/**)
            "/api/rules/**",        // RUL 생활수칙
            "/api/schedules/**",    // CAL 생활 달력·세금 상세
            "/api/events/**"        // EVE 행사 목록·상세·추천
    };

    /** 비로그인 사용자도 등록할 수 있는 API (POST 만 허용) */
    private static final String[] PUBLIC_POST = {
            "/api/reports",         // COM-01 콘텐츠 오류 신고 (guest_session_id)
            "/api/translations/**"  // RUL-07 고지서 사진 번역 (사진은 저장하지 않음)
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // SPA + 세션 쿠키: XSRF-TOKEN 쿠키 값을 X-XSRF-TOKEN 헤더로 보내야 한다 (GET 제외)
                .csrf(csrf -> csrf.spa())
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
                        // ADM-01: 관리 API·운영 지표는 관리자만
                        .requestMatchers("/api/admin/**", "/actuator/**").hasRole("ADMIN")
                        .requestMatchers(PUBLIC_ANY_METHOD).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET).permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST).permitAll()
                        // 그 외 (/api/members/me/**, /api/notifications/** 등) 는 로그인 필요
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

    /** ID·비밀번호 검증. ID가 없을 때와 비밀번호가 틀릴 때를 구분하지 않는다 (M-08). */
    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
