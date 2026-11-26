package com.siot.backend.event.collect;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static com.siot.backend.event.collect.EventTestFixtures.GGC_URL;
import static com.siot.backend.event.collect.EventTestFixtures.props;
import static com.siot.backend.event.collect.EventTestFixtures.resource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** EVE-01 지지씨: 실제 응답 샘플 100건 (2026-11-19 등록분까지, 시흥 3건) */
class GgcEventSourceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 20);
    private static final String EMPTY = "{\"INFO\":0,\"DATA\":[]}";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private GgcEventSource source(int maxPages) {
        return new GgcEventSource(builder.build(), props(null, maxPages).ggc(), JsonMapper.builder().build());
    }

    private void respond(int page, String body) {
        server.expect(requestTo(GGC_URL + "?page=" + page + "&perpage=100"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    @Test
    void keepsOnlyCurrentEventsAndStopsOnEmptyPage() {
        respond(0, resource("ggc-playongoing-sample.json"));
        respond(1, EMPTY);

        FetchResult result = source(10).fetch(TODAY);

        server.verify();
        assertThat(result.fetchedCount()).isEqualTo(100);
        assertThat(result.events()).hasSize(63)
                .allSatisfy(event -> assertThat(event.endsOn()).isAfterOrEqualTo(TODAY));
    }

    @Test
    void stopsWhenWholePageHasEnded() {
        String ended = "{\"INFO\":0,\"DATA\":[" + item("old-1", "지난 공연", "2026-10-01", "2026-10-02", "") + ","
                + item("old-2", "지난 전시", "2026-09-01", "2026-11-19", "") + "]}";
        respond(0, resource("ggc-playongoing-sample.json"));
        respond(1, ended);

        FetchResult result = source(10).fetch(TODAY);

        server.verify();   // page=2 는 요청하지 않는다
        assertThat(result.fetchedCount()).isEqualTo(102);
        assertThat(result.events()).hasSize(63);
    }

    @Test
    void stopsAtMaxPages() {
        String current = "{\"INFO\":0,\"DATA\":[" + item("x", "진행 중 공연", "2026-11-20", "2026-11-30", "") + "]}";
        respond(0, current);
        respond(1, current);

        FetchResult result = source(2).fetch(TODAY);

        server.verify();
        assertThat(result.fetchedCount()).isEqualTo(2);
    }

    /** 시흥: inarea·subject 의 "시흥" 또는 homepage 의 siheung.go.kr. 샘플에서 정확히 3건 */
    @Test
    void marksSiheungEventsInSample() {
        respond(0, resource("ggc-playongoing-sample.json"));
        respond(1, EMPTY);

        List<CollectedEvent> siheung = source(10).fetch(TODAY).events().stream()
                .filter(event -> EventRegion.SIHEUNG.equals(event.regionCode())).toList();

        assertThat(siheung).extracting(CollectedEvent::title).containsExactlyInAnyOrder(
                "미디어아트展 반고흐, 모네, 마티스 거장의 팔레트, 빛을 만나다",
                "시흥시립전통예술단 정기공연 ‘파동:바람의 울림’",
                "제9회 시흥시 합창 페스티벌");
    }

    /** 첫 행사: "enddate:" 키, "10:00~17:00" → 시작·끝 시각, homepage 를 원문 링크로, 장소·설명 없음 */
    @Test
    void mapsFieldsOfFirstSampleEvent() {
        respond(0, resource("ggc-playongoing-sample.json"));
        respond(1, EMPTY);

        CollectedEvent first = source(10).fetch(TODAY).events().getFirst();

        assertThat(first.externalId()).isEqualTo("https://ggc.ggcf.kr/cultureEvents/view/6afe5e28a2fdae731859a1bb");
        assertThat(first.startsOn()).isEqualTo(LocalDate.of(2026, 11, 13));
        assertThat(first.endsOn()).isEqualTo(LocalDate.of(2026, 12, 29));
        assertThat(first.startsAt()).isEqualTo(LocalTime.of(10, 0));
        assertThat(first.endsAt()).isEqualTo(LocalTime.of(17, 0));
        assertThat(first.priceInfo()).isEqualTo("무료");
        assertThat(first.sourceUrl()).startsWith("https://www.siheung.go.kr/sac/portal/exhibition/view.do");
        assertThat(first.venue()).isNull();
        assertThat(first.description()).isNull();
    }

    @Test
    void endDateFallsBackToStartAndBadDatesAreSkipped() {
        String body = "{\"INFO\":0,\"DATA\":["
                + "{\"href\":\"h1\",\"subject\":\"종료일 없는 공연\",\"startdate\":\"2026-11-25\",\"intime\":\"19:30 (80분)\","
                + "\"homepage\":\"\",\"inarea\":\"수원시\"},"
                + item("h2", "날짜 형식 다름", "2026.11.25", "2026.11.26", "") + ","
                + item("h3", "있을 수 없는 날짜", "2026-02-30", "2026-03-01", "") + "]}";
        respond(0, body);
        respond(1, EMPTY);

        List<CollectedEvent> events = source(10).fetch(TODAY).events();

        assertThat(events).hasSize(1);
        CollectedEvent event = events.getFirst();
        assertThat(event.endsOn()).isEqualTo(LocalDate.of(2026, 11, 25));
        assertThat(event.startsAt()).isEqualTo(LocalTime.of(19, 30));
        assertThat(event.endsAt()).isNull();
        assertThat(event.sourceUrl()).isEqualTo("h1");   // homepage 가 비면 지지씨 상세 URL
        assertThat(event.regionCode()).isEqualTo(EventRegion.GYEONGGI);
    }

    @Test
    void failsOnServerErrorOrBrokenJson() {
        server.expect(requestTo(GGC_URL + "?page=0&perpage=100")).andRespond(withServerError());
        assertThatThrownBy(() -> source(10).fetch(TODAY)).isInstanceOf(EventSourceException.class);

        server.reset();
        respond(0, "<html>점검 중</html>");
        assertThatThrownBy(() -> source(10).fetch(TODAY)).isInstanceOf(EventSourceException.class);

        server.reset();
        respond(0, "{\"INFO\":0}");
        assertThatThrownBy(() -> source(10).fetch(TODAY)).isInstanceOf(EventSourceException.class)
                .hasMessageContaining("DATA");
    }

    private static String item(String href, String subject, String start, String end, String inarea) {
        return "{\"href\":\"" + href + "\",\"subject\":\"" + subject + "\",\"startdate\":\"" + start
                + "\",\"enddate:\":\"" + end + "\",\"intime\":\"\",\"incost\":\"\",\"inarea\":\"" + inarea
                + "\",\"homepage\":\"\"}";
    }
}
