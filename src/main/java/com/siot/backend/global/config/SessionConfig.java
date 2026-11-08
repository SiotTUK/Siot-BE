package com.siot.backend.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * 세션 쿠키 설정.
 * 실행 환경(내장 서버, 테스트 MockMvc 등)과 관계없이 같은 쿠키 속성을 쓰도록 명시적으로 등록한다.
 * 값은 application.yml 의 server.servlet.session.cookie.* 를 그대로 사용한다.
 */
@Configuration
public class SessionConfig {

    @Bean
    public CookieSerializer cookieSerializer(
            @Value("${server.servlet.session.cookie.name:SIOT_SESSION}") String name,
            @Value("${server.servlet.session.cookie.same-site:lax}") String sameSite,
            @Value("${server.servlet.session.cookie.secure:false}") boolean secure) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName(name);
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setSameSite(capitalize(sameSite));
        serializer.setUseSecureCookie(secure);
        return serializer;
    }

    private static String capitalize(String value) {
        return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1).toLowerCase();
    }
}
