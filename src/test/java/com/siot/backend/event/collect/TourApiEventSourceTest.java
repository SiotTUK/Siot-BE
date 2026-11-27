package com.siot.backend.event.collect;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;

import static com.siot.backend.event.collect.EventTestFixtures.TOURAPI_DETAIL_URL;
import static com.siot.backend.event.collect.EventTestFixtures.TOURAPI_URL;
import static com.siot.backend.event.collect.EventTestFixtures.props;
import static com.siot.backend.event.collect.EventTestFixtures.resource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * EVE-01 TourAPI searchFestival2: 실제 응답 샘플에서 시흥 3건 + 서울 금천구 시흥동 1건 + 다른 지역 26건.
 * 저장할 행사마다 detailCommon2 로 원문 링크·설명을 받는다 (갯골축제는 고정 응답, MOIM 은 서버 오류)
 */
class TourApiEventSourceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 1);

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();

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
        expectDetails();

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
        // EVE-10 카테고리: 신분류 lclsSystm2 EV01 → 축제. 상세 조회로 다시 만들어도 남는다
        assertThat(result.events()).extracting(CollectedEvent::category)
                .containsOnly(com.siot.backend.event.domain.EventCategory.FESTIVAL);
    }

    /** 갯골축제: homepage 의 a 태그에서 URL 만, overview 의 br 은 줄바꿈. MOIM: 상세 조회가 실패해도 목록 값으로 저장 */
    @Test
    void fillsSourceUrlAndDescriptionFromDetailAndKeepsEventWhenDetailFails() {
        server.expect(requestTo(startsWith(TOURAPI_URL + "?")))
                .andRespond(withSuccess(resource("tourapi-searchFestival2-sample.json"), MediaType.APPLICATION_JSON));
        expectDetails();

        FetchResult result = source("test-key").fetch(TODAY);

        server.verify();
        CollectedEvent festival = result.events().stream()
                .filter(event -> event.externalId().equals("142197")).findFirst().orElseThrow();
        assertThat(festival.sourceUrl()).isEqualTo("https://www.siheung.go.kr/gaetgol/main.do?menu=1&tab=2");
        assertThat(festival.description()).isEqualTo("시흥갯골축제는 내만갯골과 소금창고가 있는 시흥갯골생태공원에서 열리는 축제이다.\n\n"
                + "갯골 생태 체험, 공연 & 전시가 열린다.\n가족과 함께 즐길 수 있다.");
        CollectedEvent moim = result.events().stream()
                .filter(event -> event.externalId().equals("2833186")).findFirst().orElseThrow();
        assertThat(moim.title()).isEqualTo("MOIM International");
        assertThat(moim.sourceUrl()).isNull();
        assertThat(moim.description()).isNull();
        // BE #19 사진: firstimage (상세가 실패해도 목록의 사진은 남는다)
        assertThat(festival.thumbnailUrl()).isEqualTo("https://tong.visitkorea.or.kr/cms/resource/09/3528809_image2_1.jpg");
        assertThat(moim.thumbnailUrl()).isEqualTo("https://tong.visitkorea.or.kr/cms/resource/59/4104859_image2_1.jpg");
    }

    /** BE #19: firstimage 가 없으면 firstimage2, http(s) 가 아니거나 비면 null */
    @Test
    void imageFallsBackToSecondImageAndIgnoresNonHttp() throws Exception {
        tools.jackson.databind.json.JsonMapper mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        assertThat(TourApiEventSource.image(mapper.readTree("{\"firstimage\":\"\",\"firstimage2\":\"http://a.test/2.jpg\"}")))
                .isEqualTo("https://a.test/2.jpg");
        assertThat(TourApiEventSource.image(mapper.readTree("{\"firstimage\":\" https://a.test/1.jpg \"}")))
                .isEqualTo("https://a.test/1.jpg");
        assertThat(TourApiEventSource.image(mapper.readTree("{\"firstimage\":\"javascript:alert(1)\"}"))).isNull();
        assertThat(TourApiEventSource.image(mapper.readTree("{}"))).isNull();
    }

    /** 저장할 시흥 행사 2건의 상세 요청 (순서 무관하게 받도록 서버는 ignoreExpectOrder) */
    private void expectDetails() {
        server.expect(requestTo(startsWith(TOURAPI_DETAIL_URL + "?")))
                .andExpect(queryParam("contentId", "142197"))
                .andExpect(queryParam("serviceKey", "test-key"))
                .andRespond(withSuccess(resource("tourapi-detailCommon2-sample.json"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(TOURAPI_DETAIL_URL + "?")))
                .andExpect(queryParam("contentId", "2833186"))
                .andRespond(withServerError());
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
