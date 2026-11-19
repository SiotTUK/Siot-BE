package com.siot.backend.global.common;

import java.util.Set;

/** 서비스 언어 (화면·메일 문구). ko 가 기본이다. */
public final class SupportedLanguages {

    public static final String DEFAULT = "ko";
    public static final Set<String> ALL = Set.of("ko", "en", "zh", "vi");
    /** 요청 DTO 의 {@code @Pattern} 용 */
    public static final String PATTERN = "ko|en|zh|vi";

    private SupportedLanguages() {
    }

    public static boolean isSupported(String language) {
        return language != null && ALL.contains(language);
    }

    /** 지원하지 않거나 비어 있으면 ko */
    public static String orDefault(String language) {
        return isSupported(language) ? language : DEFAULT;
    }
}
