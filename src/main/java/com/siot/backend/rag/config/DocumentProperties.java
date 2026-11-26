package com.siot.backend.rag.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 문서 적재 설정 (application.yml 의 siot.document.*, ADM-02~04).
 *
 * @param storageDir          업로드한 PDF 저장 폴더 (DOCUMENT_STORAGE_DIR)
 * @param embeddingBatchSize  임베딩 한 번에 보낼 조각 수 (Gemini batchEmbedContents 최대 100)
 * @param embeddingBatchPause 묶음 사이 간격 (무료 등급 분당 한도)
 * @param embeddingRetryWait  429·503 응답 뒤 다시 보내기 전 대기
 * @param embeddingMaxRetries 429·503 재시도 횟수. 넘으면 FAILED [EMBEDDING]
 */
@Validated
@ConfigurationProperties(prefix = "siot.document")
public record DocumentProperties(
        @NotBlank String storageDir,
        @Positive int embeddingBatchSize,
        @NotNull Duration embeddingBatchPause,
        @NotNull Duration embeddingRetryWait,
        @PositiveOrZero int embeddingMaxRetries
) {
}
