package com.siot.backend.event.collect;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 행사 수집 설정 (application.yml 의 siot.event.*, EVE-01·EVE-02).
 *
 * @param apiKey            공공데이터포털 키 (EVENT_API_KEY). TourAPI·축제표준데이터에 쓴다. 비어 있으면 두 출처는 FAILED
 * @param httpTimeout       외부 API 연결·응답 대기 시간
 * @param translationPause  번역 호출 사이 간격 (Gemini 무료 등급 분당 한도)
 * @param ggc               경기문화재단 지지씨 (인증키 없음)
 * @param tourapi           한국관광공사 TourAPI searchFestival2
 * @param festival          전국문화축제표준데이터
 */
@Validated
@ConfigurationProperties(prefix = "siot.event")
public record EventProperties(
        String apiKey,
        @NotNull Duration httpTimeout,
        @NotNull Duration translationPause,
        @Valid @NotNull Ggc ggc,
        @Valid @NotNull TourApi tourapi,
        @Valid @NotNull Festival festival
) {

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * @param perPage  한 페이지 행사 수 (최대 100)
     * @param maxPages 안전장치. 등록일 최신순이라 뒤 페이지는 거의 끝난 행사
     */
    public record Ggc(@NotBlank String baseUrl, @Positive int perPage, @Positive int maxPages) {
    }

    /**
     * @param lookbackDays 요청 eventStartDate = 오늘 - lookbackDays (진행 중인 축제가 빠지지 않게). 저장은 종료일 >= 오늘만
     * @param regionCode   법정동 시도 코드 (경기 41)
     * @param districtCode 법정동 시군구 코드 (시흥 390)
     */
    public record TourApi(@NotBlank String baseUrl, @Positive int pageSize, @Positive int maxPages,
                          @PositiveOrZero int lookbackDays, @NotBlank String regionCode,
                          @NotBlank String districtCode) {
    }

    /**
     * @param addressPrefix 저장할 행사의 소재지 주소 시작 ("경기도 시흥시")
     */
    public record Festival(@NotBlank String baseUrl, @Positive int pageSize, @Positive int maxPages,
                           @NotBlank String addressPrefix) {
    }
}
