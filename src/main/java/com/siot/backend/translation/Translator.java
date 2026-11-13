package com.siot.backend.translation;

import com.siot.backend.rag.ai.AiUnavailableException;

import java.util.List;
import java.util.Map;

/**
 * 공통 번역 모듈 (COM-02, EVE-02, RUL-13). 한국어 원문을 서비스 언어로 번역한다.
 * 실패하면 {@link AiUnavailableException}. 원문 유지·재시도는 호출하는 쪽에서 정한다 (COM-02: 번역 준비 중).
 */
public interface Translator {

    /** 번역 대상 언어 (ko 는 원문) */
    List<String> TARGET_LANGUAGES = List.of("en", "zh", "vi");

    /**
     * @param targetLanguage ko, en, zh, vi. ko 이면 원문 그대로
     * @return 번역문. 원문이 null 이거나 비어 있으면 원문 그대로
     * @throws IllegalArgumentException 지원하지 않는 언어
     */
    String translate(String text, String targetLanguage);

    /**
     * en, zh, vi 를 한 번의 호출로 번역한다.
     *
     * @return 언어 코드 → 번역문 (en, zh, vi 순서). 원문이 null 이거나 비어 있으면 모든 값이 원문 그대로
     */
    Map<String, String> translateAll(String text);
}
