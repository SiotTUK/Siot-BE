package com.siot.backend.member.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Locale;

/**
 * ADM-01: 처음부터 관리자로 지정할 이메일 (환경변수 SIOT_ADMIN_EMAILS, 쉼표 구분).
 * 앱 시작 시 이미 가입한 회원은 관리자로 올리고, 아직 가입 전이면 가입할 때 관리자가 된다.
 */
@ConfigurationProperties(prefix = "siot.admin")
public record AdminProperties(List<String> emails) {

    public AdminProperties {
        emails = emails == null ? List.of() : emails.stream()
                .filter(e -> e != null && !e.isBlank())
                .map(e -> e.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    /** @param email 소문자로 정규화한 이메일 */
    public boolean isAdminEmail(String email) {
        return email != null && emails.contains(email);
    }
}
