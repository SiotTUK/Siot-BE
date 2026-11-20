package com.siot.backend.event.collect;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;

import static com.siot.backend.event.collect.EventTestFixtures.TOURAPI_URL;
import static com.siot.backend.event.collect.EventTestFixtures.props;
import static com.siot.backend.event.collect.EventTestFixtures.resource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** EVE-01 TourAPI searchFestival2: 실제 응답 샘플에서 시흥 3건 + 서울 금천구 시흥동 1건 + 다른 지역 26건 */
class TourApiEventSourceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 1);

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private TourApiEventSource source(String apiKey) {
        return new TourApiEventSource(builder.build(), props(apiKey, 10), JsonMapper.builder().build());
    }

    /** 요청에서 법정동 코드로 거르고, 1년 전부터 받아 종료일 >= 오늘인 것만. 금천구 시흥동은 빠진다 */
    @Test
    void requestsSiheungSinceAYearAgoAndKeepsCurrentOnes() {
        server.expect(requestTo(startsWith(TOURAPI_URL + "?")))
                .andExpect(queryParam("serviceKey", "test-key"))
                .andExpect(queryParam("eventStartDate", "20250901"))
                .andExpect(queryParam("lDongRegnCd", "41"))
                .andExpect(queryParam("lDongSignguCd", "390"))
                .andExpect(queryParam("_type", "json"))
                .andExpect(queryParam("pageNo", "1"))
                .andRespond(withSuccess(resource("tourapi-searchFestival2-sample.json"), MediaType.APPLICATION_JSON));

        FetchResult result = source("test-key").fetch(TODAY);

        server.verify();
        assertThat(result.fetchedCount()).isEqualTo(30);
        // WSL 서핑대회(7/3~7/5)는 끝났다
        assertThat(result.events()).extracting(CollectedEvent::title)
                .containsExactlyInAnyOrder("시흥갯골축제", "MOIM International");
        CollectedEvent festival = result.events().stream()
                .filter(event -> event.title().equals("시흥갯골축제")).findFirst().orElseThrow();
        assertThat(festival.startsOn()).isEqualTo(LocalDate.of(2026, 9, 18));
        assertThat(festival.endsOn()).isEqualTo(LocalDate.of(2026, 9, 20));
        assertThat(festival.regionCode()).isEqualTo(EventRegion.SIHEUNG);
        assertThat(festival.venue()).contains("시흥시");
        assertThat(festival.externalId()).isNotBlank();
    }

    /** 키 문자에 +, /, = 가 있어도 한 번만 인코딩해서 보낸다 (공공데이터포털 Decoding 키) */
    @Test
    void encodesServiceKeyOnce() {
        server.expect(queryParam("serviceKey", "a%2Bb%2Fc%3D%3D"))
                .andRespond(withSuccess("{\"response\":{\"header\":{\"resultCode\":\"0000\",\"resultMsg\":\"OK\"},"
                        + "\"body\":{\"items\":\"\",\"numOfRows\":1000,\"pageNo\":1,\"totalCount\":0}}}",
                        MediaType.APPLICATION_JSON));

        FetchResult result = source("a+b/c==").fetch(TODAY);

        server.verify();
        assertThat(result.events()).isEmpty();
    }

    @Test
    void failsWithoutKeyOrOnServiceError() {
        assertThatThrownBy(() -> source("").fetch(TODAY)).isInstanceOf(EventSourceException.class)
                .hasMessageContaining("EVENT_API_KEY");

        server.expect(requestTo(startsWith(TOURAPI_URL)))
                .andRespond(withSuccess("<OpenAPI_ServiceResponse><cmmMsgHeader><errMsg>SERVICE ERROR</errMsg>"
                        + "</cmmMsgHeader></OpenAPI_ServiceResponse>", MediaType.APPLICATION_XML));
        assertThatThrownBy(() -> source("test-key").fetch(TODAY)).isInstanceOf(EventSourceException.class);
    }
}
