package com.siot.backend.event.collect;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 지지씨 API 를 page=0 한 번만 불러 파싱되는지 확인한다 (EVE-01). 평소 ./gradlew test 와 CI 에서는 건너뛴다.
 * <pre>
 * EVENT_LIVE=true ./gradlew test --tests '*GgcLiveTest' --rerun -i
 * </pre>
 */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "EVENT_LIVE", matches = "true")
class GgcLiveTest {

    @Test
    void parsesFirstPageOfRealApi() {
        EventProperties.Ggc props = new EventProperties.Ggc("https://ggc.ggcf.kr/open/json/playongoing", 100, 1);
        RestClient client = new EventCollectConfig().eventRestClient(new EventProperties(null, Duration.ofSeconds(30),
                Duration.ZERO, props, null, null));
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        FetchResult result = new GgcEventSource(client, props, JsonMapper.builder().build()).fetch(today);

        List<CollectedEvent> siheung = result.events().stream()
                .filter(event -> EventRegion.SIHEUNG.equals(event.regionCode())).toList();
        System.out.printf("GGC page 0: fetched %d, current (ends >= %s) %d, Siheung %d%n",
                result.fetchedCount(), today, result.events().size(), siheung.size());
        siheung.forEach(event -> System.out.printf("  [SIHEUNG] %s | %s ~ %s %s | %s%n", event.title(),
                event.startsOn(), event.endsOn(), event.startsAt() == null ? "" : event.startsAt(), event.sourceUrl()));
        long withTime = result.events().stream().filter(event -> event.startsAt() != null).count();
        System.out.printf("  with start time: %d / %d%n", withTime, result.events().size());

        assertThat(result.fetchedCount()).isPositive();
        assertThat(result.events()).allSatisfy(event -> {
            assertThat(event.externalId()).startsWith("https://ggc.ggcf.kr/");
            assertThat(event.endsOn()).isAfterOrEqualTo(today);
        });
    }
}
