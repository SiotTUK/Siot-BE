package com.siot.backend.translation.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** RUL-07 고지서 전용 모델·제한 시간 (siot.translation.bill-model, bill-timeout) */
class TranslationPropertiesTest {

    @Test
    void blankBillModelFallsBackToChatModel() {
        assertThat(properties(null).billModelOr("chat-model")).isEqualTo("chat-model");
        assertThat(properties("").billModelOr("chat-model")).isEqualTo("chat-model");
        assertThat(properties("  ").billModelOr("chat-model")).isEqualTo("chat-model");
        assertThat(properties(" bill-model ").billModelOr("chat-model")).isEqualTo("bill-model");
    }

    @Test
    void bindsBillModelAndTimeout() {
        TranslationProperties bound = new Binder(new MapConfigurationPropertySource(Map.of(
                "siot.translation.bill-model", "gemini-3-flash-preview",
                "siot.translation.bill-timeout", "45s",
                "siot.translation.bill-max-file-size", "10MB",
                "siot.translation.bill-retry-wait", "2s",
                "siot.translation.bill-rate-limit.max-requests", "10",
                "siot.translation.bill-rate-limit.window", "1h",
                "siot.translation.bill-rate-limit.min-interval", "2s")))
                .bind("siot.translation", TranslationProperties.class).get();

        assertThat(bound.billModel()).isEqualTo("gemini-3-flash-preview");
        assertThat(bound.billTimeout()).isEqualTo(Duration.ofSeconds(45));
        assertThat(bound.billMaxFileSize()).isEqualTo(DataSize.ofMegabytes(10));
        assertThat(bound.billRateLimit().maxRequests()).isEqualTo(10);
    }

    private static TranslationProperties properties(String billModel) {
        return new TranslationProperties(billModel, Duration.ofSeconds(45), DataSize.ofMegabytes(10), Duration.ZERO,
                new TranslationProperties.RateLimit(10, Duration.ofHours(1), Duration.ofSeconds(2)));
    }
}
