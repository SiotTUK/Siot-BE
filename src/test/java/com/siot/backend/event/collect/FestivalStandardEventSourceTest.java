package com.siot.backend.event.collect;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;

import static com.siot.backend.event.collect.EventTestFixtures.FESTIVAL_URL;
import static com.siot.backend.event.collect.EventTestFixtures.props;
import static com.siot.backend.event.collect.EventTestFixtures.resource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * EVE-01 전국문화축제표준데이터. 키 승인 전이라 실제 JSON 을 받아 보지 못했다:
 * CSV 샘플(.claude-docs/festival-standard-sample.csv, 시흥 5건)을 표준 항목 이름으로 옮기고
 * 수원 1건, 서울 금천구 시흥동 1건을 더한 고정 응답으로 확인한다.
 */
class FestivalStandardEventSourceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 15);

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private FestivalStandardEventSource source(String apiKey) {
        return new FestivalStandardEventSource(builder.build(), props(apiKey, 10), JsonMapper.builder().build());
    }

    @Test
    void keepsCurrentSiheungFestivalsWithVenueAndDescription() {
        server.expect(requestTo(startsWith(FESTIVAL_URL + "?")))
                .andExpect(queryParam("serviceKey", "test-key"))
                .andExpect(queryParam("type", "json"))
                .andExpect(queryParam("pageNo", "1"))
                .andRespond(withSuccess(resource("festival-standard-sample.json"), MediaType.APPLICATION_JSON));

        FetchResult result = source("test-key").fetch(TODAY);

        server.verify();
        assertThat(result.fetchedCount()).isEqualTo(7);
        // 거북섬(9/12)은 끝났고, 수원·서울 금천구 시흥동은 시흥시 주소가 아니다
        assertThat(result.events()).extracting(CollectedEvent::title).containsExactlyInAnyOrder(
                "제21회 시흥갯골축제", "제13회 시흥월곶포구축제", "오이도 축제", "세계 커피콩 축제");
        CollectedEvent gaetgol = result.events().stream()
                .filter(event -> event.title().equals("제21회 시흥갯골축제")).findFirst().orElseThrow();
        assertThat(gaetgol.externalId()).isEqualTo("제21회 시흥갯골축제|2026-09-18");
        assertThat(gaetgol.venue()).isEqualTo("시흥갯골생태공원");
        assertThat(gaetgol.description()).isNotBlank();
        assertThat(gaetgol.regionCode()).isEqualTo(EventRegion.SIHEUNG);
        // 지번 주소만 있는 행사도 시흥으로 본다
        assertThat(result.events()).extracting(CollectedEvent::title).contains("세계 커피콩 축제");
    }

    /** 키 승인 전 실제 응답 (SERVICE_KEY_IS_NOT_REGISTERED_ERROR) */
    @Test
    void failsWhenKeyIsNotRegistered() {
        server.expect(requestTo(startsWith(FESTIVAL_URL)))
                .andRespond(withSuccess("""
                        {"OpenAPI_ServiceResponse": {"cmmMsgHeader": {"errMsg": "SERVICE_KEY_IS_NOT_REGISTERED_ERROR",
                         "returnAuthMsg": "등록되지 않은 서비스키", "returnReasonCode": "30"}}}""", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> source("test-key").fetch(TODAY)).isInstanceOf(EventSourceException.class)
                .hasMessageContaining("SERVICE_KEY_IS_NOT_REGISTERED_ERROR");
    }

    @Test
    void noDataCodeGivesNoEvents() {
        server.expect(requestTo(startsWith(FESTIVAL_URL)))
                .andRespond(withSuccess("{\"response\":{\"header\":{\"resultCode\":\"03\",\"resultMsg\":\"NODATA_ERROR\"}}}",
                        MediaType.APPLICATION_JSON));

        assertThat(source("test-key").fetch(TODAY).events()).isEmpty();
    }
}
