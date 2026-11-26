package com.siot.backend.event.collect;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 실제 전국문화축제표준데이터 API 를 운영 설정(application.yml siot.event.festival)으로 한 번 불러
 * 응답을 읽을 수 있는지와 시흥 행사 수를 출력한다 (EVE-01). 평소 ./gradlew test 와 CI 에서는 건너뛴다.
 * <pre>
 * EVENT_LIVE=true EVENT_API_KEY=... ./gradlew test --tests '*FestivalStandardLiveTest' --rerun -i
 * </pre>
 */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "EVENT_LIVE", matches = "true")
class FestivalStandardLiveTest {

    @Test
    void readsRealApiAndCountsSiheungFestivals() {
        String apiKey = System.getenv("EVENT_API_KEY");
        assumeTrue(apiKey != null && !apiKey.isBlank(), "EVENT_API_KEY 가 없어 건너뜀");
        EventProperties.Festival festival = new EventProperties.Festival(
                "https://api.data.go.kr/openapi/tn_pubr_public_cltur_fstvl_api", 1000, 5, "경기도 시흥시");
        EventProperties props = new EventProperties(apiKey, Duration.ofSeconds(30), Duration.ZERO, null, null,
                festival);
        RestClient client = new EventCollectConfig().eventRestClient(props);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        FetchResult result = new FestivalStandardEventSource(client, props, JsonMapper.builder().build()).fetch(today);

        // 저장 대상은 모두 시흥 주소이고 종료일 >= 오늘
        System.out.printf("FESTIVAL_STD: fetched %d (all regions), Siheung current (ends >= %s) %d%n",
                result.fetchedCount(), today, result.events().size());
        result.events().forEach(event -> System.out.printf("  [SIHEUNG] %s | %s ~ %s | %s | %s%n", event.title(),
                event.startsOn(), event.endsOn(), event.venue(), event.sourceUrl()));

        assertThat(result.fetchedCount()).isPositive();
        assertThat(result.events()).allSatisfy(event -> {
            assertThat(event.regionCode()).isEqualTo(EventRegion.SIHEUNG);
            assertThat(event.endsOn()).isAfterOrEqualTo(today);
        });
    }
}
