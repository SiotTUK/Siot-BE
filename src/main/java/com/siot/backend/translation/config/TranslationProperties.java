package com.siot.backend.translation.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 번역 설정 (application.yml 의 siot.translation.*).
 *
 * @param billModel       고지서 사진(RUL-07)을 읽을 Gemini 모델 (GEMINI_BILL_MODEL). 비어 있으면 siot.gemini.chat-model
 * @param billTimeout     고지서 사진 읽기 1회 호출 제한 시간 (GEMINI_BILL_TIMEOUT). 사진은 글보다 오래 걸린다
 * @param billMaxFileSize 고지서 사진 최대 크기. 넘으면 413
 * @param billRetryWait   Gemini 429·503 뒤 한 번 다시 보내기 전 대기
 * @param billRateLimit   회원당 고지서 번역 횟수 제한 (무료 한도 보호)
 */
@Validated
@ConfigurationProperties(prefix = "siot.translation")
public record TranslationProperties(
        String billModel,
        @NotNull Duration billTimeout,
        @NotNull DataSize billMaxFileSize,
        @NotNull Duration billRetryWait,
        @Valid @NotNull RateLimit billRateLimit
) {

    /** bill-model 이 비어 있으면 챗봇과 같은 채팅 모델 */
    public String billModelOr(String chatModel) {
        return billModel == null || billModel.isBlank() ? chatModel : billModel.strip();
    }

    /**
     * @param maxRequests window 동안 최대 요청 수
     * @param window      횟수를 세는 기간 (첫 요청부터)
     * @param minInterval 연속 요청 최소 간격 (두 번 누름 방지)
     */
    public record RateLimit(
            @Positive int maxRequests,
            @NotNull Duration window,
            @NotNull Duration minInterval
    ) {
    }
}
