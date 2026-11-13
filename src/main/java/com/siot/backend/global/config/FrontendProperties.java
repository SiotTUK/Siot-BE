package com.siot.backend.global.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param baseUrl 프론트엔드 주소. 메일 안의 링크를 만들 때 사용한다. 예: https://siot.example.com
 */
@Validated
@ConfigurationProperties(prefix = "siot.frontend")
public record FrontendProperties(@NotBlank String baseUrl) {

    public String url(String path) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return base + path;
    }
}
