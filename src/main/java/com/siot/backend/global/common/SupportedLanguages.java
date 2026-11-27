package com.siot.backend.global.common;

import com.siot.backend.global.exception.BusinessException;
import com.siot.backend.global.exception.ErrorCode;

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

    /** 지원하지 않거나 비어 있으면 ko. 저장된 값·내부 값용 (요청 쿼리는 {@link #requireValid}) */
    public static String orDefault(String language) {
        return isSupported(language) ? language : DEFAULT;
    }

    /**
     * 요청 쿼리 {@code language} 검사 (모든 API 공통 규칙). 생략·빈 값이면 null 을 돌려주고 각 API 의 기본값
     * (ko 또는 회원 언어)을 쓴다. 지원하지 않는 값은 조용히 바꾸지 않고 400 {@code INVALID_INPUT}.
     */
    public static String requireValid(String language) {
        if (language == null || language.isBlank()) {
            return null;
        }
        if (!isSupported(language)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "language: ko, en, zh, vi 중 하나로 보내 주세요.");
        }
        return language;
    }
}
