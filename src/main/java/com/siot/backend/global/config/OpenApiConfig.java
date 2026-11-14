package com.siot.backend.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import com.siot.backend.global.config.openapi.OpenApiErrorResponseCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI (/swagger-ui.html) 설정.
 * <p>
 * 인증은 세션 쿠키(SIOT_SESSION)다. Swagger UI 에서 쓰려면 같은 브라우저에서
 * {@code GET /api/auth/csrf} → {@code POST /api/auth/login} 을 먼저 실행한다.
 * POST·PUT·PATCH·DELETE 의 X-XSRF-TOKEN 헤더는 Swagger UI 가 XSRF-TOKEN 쿠키에서 자동으로 넣는다
 * (springdoc.swagger-ui.csrf.enabled).
 */
@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true", matchIfMissing = true)
public class OpenApiConfig {

    static final String SESSION_SCHEME = "session";

    @Bean
    public OpenAPI siotOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("웰컴시흥 (Siot) Backend API")
                        .version("v1")
                        .description("""
                                응답 형식: 성공 `{ "data": ... }`, 실패 `{ "code": "...", "message": "..." }`.

                                **사용 순서**: `GET /api/auth/csrf` → `POST /api/auth/login` (세션 쿠키 발급) → 다른 API.
                                `/api/admin/**` 는 관리자만, `/api/auth/**` 외 나머지는 로그인이 필요합니다.
                                POST·PUT·PATCH·DELETE 는 `X-XSRF-TOKEN` 헤더가 필요합니다 (이 화면에서는 자동).

                                오류 응답의 `code` 목록은 `ErrorResponse` 스키마에 있고, 각 API 의 응답 목록에
                                그 API 가 돌려줄 수 있는 코드와 예시가 HTTP 상태별로 나옵니다.
                                요약 앞의 `M-01`, `CB-11` 등은 요구사항 명세서 ID 입니다. 자세한 규칙은 저장소 `docs/`."""))
                .components(new Components().addSecuritySchemes(SESSION_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.COOKIE)
                        .name("SIOT_SESSION")
                        .description("로그인하면 발급되는 세션 쿠키 (같은 브라우저면 자동으로 전송됨)")))
                .addSecurityItem(new SecurityRequirement().addList(SESSION_SCHEME));
    }

    /** 성공 코드·오류 응답·공개 API 표시를 실제 동작에 맞춘다 */
    @Bean
    public OpenApiErrorResponseCustomizer openApiErrorResponseCustomizer() {
        return new OpenApiErrorResponseCustomizer();
    }
}
