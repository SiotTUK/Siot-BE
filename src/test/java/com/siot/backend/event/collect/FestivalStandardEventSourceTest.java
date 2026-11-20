package com.siot.backend.event.collect;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
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
 * EVE-01 전국문화축제표준데이터. 실제 응답은 response 없이 header·body 가 최상위이고 items 는 {"item": [...]}.
 * 고정 응답: 시흥 5건(CSV 샘플을 표준 항목 이름으로 옮김) + 수원 1건 + 서울 금천구 시흥동 1건 + 실제로 받은 광주 1건.
 */
class FestivalStandardEventSourceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 15);

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private FestivalStandardEventSource source(String apiKey) {
        return new FestivalStandardEventSource(builder.build(), props(apiKey, 10), JsonMapper.builder().build());
    }

    /** 한 페이지 크기만 바꾼 설정 (페이지 넘김 확인용) */
    private FestivalStandardEventSource sourceWithPageSize(int pageSize) {
        EventProperties base = props("test-key", 10);
        EventProperties props = new EventProperties(base.apiKey(), Duration.ofSeconds(5), Duration.ZERO, base.ggc(),
                base.tourapi(), new EventProperties.Festival(FESTIVAL_URL, pageSize, 5, "경기도 시흥시"));
        return new FestivalStandardEventSource(builder.build(), props, JsonMapper.builder().build());
    }

    @Test
    void keepsOnlyCurrentSiheungFestivalsFromTopLevelHeaderAndBody() {
        server.expect(requestTo(startsWith(FESTIVAL_URL + "?")))
                .andExpect(queryParam("serviceKey", "test-key"))
                .andExpect(queryParam("type", "json"))
                .andExpect(queryParam("pageNo", "1"))
                .andRespond(withSuccess(resource("festival-standard-sample.json"), MediaType.APPLICATION_JSON));

        FetchResult result = source("test-key").fetch(TODAY);

        server.verify();
        assertThat(result.fetchedCount()).isEqualTo(8);
        // 거북섬(9/12)은 끝났고, 수원·서울 금천구 시흥동·광주는 시흥시 주소가 아니다
        assertThat(result.events()).extracting(CollectedEvent::title).containsExactlyInAnyOrder(
                "제21회 시흥갯골축제", "제13회 시흥월곶포구축제", "오이도 축제", "세계 커피콩 축제");
        assertThat(result.events()).extracting(CollectedEvent::regionCode).containsOnly(EventRegion.SIHEUNG);

        CollectedEvent gaetgol = find(result, "제21회 시흥갯골축제");
        assertThat(gaetgol.externalId()).isEqualTo("제21회 시흥갯골축제|2026-09-18");
        assertThat(gaetgol.venue()).isEqualTo("시흥갯골생태공원");
        assertThat(gaetgol.description()).isNotBlank();
        // "www.…" 에 https:// 를 붙이고, 이미 있으면 그대로
        assertThat(gaetgol.sourceUrl()).isEqualTo("https://www.instagram.com/sgfestival");
        assertThat(find(result, "제13회 시흥월곶포구축제").sourceUrl())
                .isEqualTo("https://www.instagram.com/wolgotfesta/");
        // "+" 로 이은 장소·내용은 ", " 로
        CollectedEvent oido = find(result, "오이도 축제");
        assertThat(oido.venue()).isEqualTo("오이도 빨강등대, 오이도 선사유적공원");
        assertThat(oido.description()).isEqualTo("오이도 해양 체험, 불꽃놀이, 먹거리 장터");
        assertThat(oido.sourceUrl()).isNull();
    }

    /** response 로 한 번 감싼 형태도 읽는다 */
    @Test
    void stillReadsResponseWrappedEnvelope() {
        server.expect(requestTo(startsWith(FESTIVAL_URL)))
                .andRespond(withSuccess("{\"response\":{\"header\":{\"resultCode\":\"00\",\"resultMsg\":\"NORMAL SERVICE.\"},"
                        + "\"body\":{\"items\":[" + item("오이도 축제", "경기도 시흥시 오이도로 170") + "],"
                        + "\"totalCount\":1,\"numOfRows\":1000,\"pageNo\":1}}}", MediaType.APPLICATION_JSON));

        FetchResult result = source("test-key").fetch(TODAY);

        assertThat(result.events()).extracting(CollectedEvent::title).containsExactly("오이도 축제");
    }

    /** totalCount 3, 한 페이지 2건 → 2페이지에서 멈춘다 (3페이지를 부르면 server 가 실패시킨다) */
    @Test
    void pagesUntilTotalCount() {
        server.expect(queryParam("pageNo", "1"))
                .andExpect(queryParam("numOfRows", "2"))
                .andRespond(withSuccess(page("\"3\"", item("축제 1", "경기도 시흥시 1"), item("축제 2", "서울특별시 2")),
                        MediaType.APPLICATION_JSON));
        server.expect(queryParam("pageNo", "2"))
                .andRespond(withSuccess(page("\"3\"", item("축제 3", "경기도 시흥시 3")), MediaType.APPLICATION_JSON));

        FetchResult result = sourceWithPageSize(2).fetch(TODAY);

        server.verify();
        assertThat(result.fetchedCount()).isEqualTo(3);
        assertThat(result.events()).extracting(CollectedEvent::title).containsExactly("축제 1", "축제 3");
    }

    /** totalCount 가 없으면 받은 건수가 한 페이지 크기보다 적을 때 멈춘다 */
    @Test
    void pagesUntilShortPageWithoutTotalCount() {
        server.expect(queryParam("pageNo", "1"))
                .andRespond(withSuccess(page(null, item("축제 1", "경기도 시흥시 1"), item("축제 2", "경기도 시흥시 2")),
                        MediaType.APPLICATION_JSON));
        server.expect(queryParam("pageNo", "2"))
                .andRespond(withSuccess(page(null, item("축제 3", "경기도 시흥시 3")), MediaType.APPLICATION_JSON));

        FetchResult result = sourceWithPageSize(2).fetch(TODAY);

        server.verify();
        assertThat(result.events()).extracting(CollectedEvent::title).containsExactly("축제 1", "축제 2", "축제 3");
    }

    @Test
    void joinsPlusSeparatedItemsAndAddsSchemeToHomepage() {
        assertThat(FestivalStandardEventSource.joinItems("금남로+충장로 + 5.18민주광장+예술의거리 일원"))
                .isEqualTo("금남로, 충장로, 5.18민주광장, 예술의거리 일원");
        assertThat(FestivalStandardEventSource.joinItems("K-뷰티&한복쇼")).isEqualTo("K-뷰티&한복쇼");
        assertThat(FestivalStandardEventSource.joinItems("+")).isNull();
        assertThat(FestivalStandardEventSource.joinItems(" ")).isNull();

        assertThat(FestivalStandardEventSource.homepage("www.siheung.go.kr")).isEqualTo("https://www.siheung.go.kr");
        assertThat(FestivalStandardEventSource.homepage("siheung.go.kr/festa?id=1"))
                .isEqualTo("https://siheung.go.kr/festa?id=1");
        assertThat(FestivalStandardEventSource.homepage("http://recollection.kr/")).isEqualTo("http://recollection.kr/");
        assertThat(FestivalStandardEventSource.homepage("HTTPS://A.TEST")).isEqualTo("HTTPS://A.TEST");
        assertThat(FestivalStandardEventSource.homepage("없음")).isNull();
        assertThat(FestivalStandardEventSource.homepage("")).isNull();
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
    void failsOnErrorResultCode() {
        server.expect(requestTo(startsWith(FESTIVAL_URL)))
                .andRespond(withSuccess("{\"header\":{\"resultCode\":\"30\",\"resultMsg\":\"SERVICE KEY IS NOT REGISTERED\"}}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> source("test-key").fetch(TODAY)).isInstanceOf(EventSourceException.class)
                .hasMessageContaining("resultCode=30");
    }

    @Test
    void noDataCodeGivesNoEvents() {
        server.expect(requestTo(startsWith(FESTIVAL_URL)))
                .andRespond(withSuccess("{\"header\":{\"resultCode\":\"03\",\"resultMsg\":\"NODATA_ERROR\"}}",
                        MediaType.APPLICATION_JSON));

        assertThat(source("test-key").fetch(TODAY).events()).isEmpty();
    }

    // ----------------------------------------------------------------

    private static CollectedEvent find(FetchResult result, String title) {
        return result.events().stream().filter(event -> event.title().equals(title)).findFirst().orElseThrow();
    }

    /** 실제 형식 한 페이지. totalCount 가 null 이면 빼고 보낸다 */
    private static String page(String totalCount, String... items) {
        return "{\"header\":{\"resultCode\":\"00\",\"resultMsg\":\"NORMAL SERVICE.\"},\"body\":{\"items\":{\"item\":["
                + String.join(",", items) + "]}" + (totalCount == null ? "" : ",\"totalCount\":" + totalCount)
                + ",\"numOfRows\":\"2\"}}";
    }

    private static String item(String name, String roadAddress) {
        return "{\"fstvlNm\":\"" + name + "\",\"opar\":\"\",\"fstvlStartDate\":\"2026-10-01\","
                + "\"fstvlEndDate\":\"2026-10-02\",\"rdnmadr\":\"" + roadAddress + "\",\"lnmadr\":\"\"}";
    }
}
