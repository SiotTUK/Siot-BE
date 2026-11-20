package com.siot.backend.event.collect;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 경기문화재단 지지씨 문화행사 API (인증키 없음). GET {base-url}?page={0부터}&perpage=100 → {"INFO": 0, "DATA": [...]}.
 * <ul>
 *     <li>등록일 최신순이고, 이름(playongoing)과 달리 끝난 행사도 섞여 있다. DATA 가 비었거나 한 페이지가 모두
 *         끝난 행사면 멈춘다 (안전장치 max-pages)</li>
 *     <li>종료일 키는 {@code enddate:} (콜론 포함, 원본 오타). 없으면 시작일과 같게</li>
 *     <li>장소·주소·설명은 없다. {@code address} 는 늘 비어 있고 {@code inarea} 는 주최·주관이라 장소로 쓰지 않는다</li>
 *     <li>지역: inarea·subject 에 "시흥", 또는 homepage 에 siheung.go.kr 이면 시흥시, 아니면 경기도</li>
 * </ul>
 */
@Slf4j
public class GgcEventSource implements EventSource {

    static final String END_DATE_KEY = "enddate:";

    private final RestClient restClient;
    private final EventProperties.Ggc props;
    private final JsonMapper jsonMapper;

    public GgcEventSource(RestClient restClient, EventProperties.Ggc props, JsonMapper jsonMapper) {
        this.restClient = restClient;
        this.props = props;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public EventSourceType type() {
        return EventSourceType.GGC;
    }

    @Override
    public FetchResult fetch(LocalDate today) {
        List<CollectedEvent> events = new ArrayList<>();
        int fetched = 0;
        for (int page = 0; page < props.maxPages(); page++) {
            JsonNode data = fetchPage(page);
            if (data.isEmpty()) {
                break;
            }
            fetched += data.size();
            boolean anyCurrent = false;
            for (JsonNode item : data) {
                CollectedEvent event = parse(item);
                if (event == null || event.endsOn().isBefore(today)) {
                    continue;
                }
                anyCurrent = true;
                events.add(event);
            }
            if (!anyCurrent) {
                break;
            }
        }
        return new FetchResult(fetched, events);
    }

    private JsonNode fetchPage(int page) {
        String body;
        try {
            body = restClient.get()
                    .uri(props.baseUrl() + "?page={page}&perpage={perPage}", page, props.perPage())
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw new EventSourceException("GGC page " + page + " request failed: " + e.getMessage(), e);
        }
        try {
            JsonNode root = body == null ? null : jsonMapper.readTree(body);
            if (root == null || !root.isObject() || !root.path("DATA").isArray()) {
                throw new EventSourceException("GGC page " + page + ": unexpected response (no DATA array)");
            }
            return root.path("DATA");
        } catch (JacksonException e) {
            throw new EventSourceException("GGC page " + page + ": response is not JSON", e);
        }
    }

    /** 행사 하나. 필수 값(href, subject, 시작일)이 없거나 날짜 형식이 다르면 null */
    CollectedEvent parse(JsonNode item) {
        String href = EventTexts.clean(text(item, "href"));
        String title = EventTexts.title(text(item, "subject"));
        String startText = text(item, "startdate");
        String endText = item.has(END_DATE_KEY) ? text(item, END_DATE_KEY) : text(item, "enddate");
        LocalDate startsOn = EventTexts.isoDate(startText);
        LocalDate endsOn = EventTexts.clean(endText) == null ? startsOn : EventTexts.isoDate(endText);
        if (href == null || title == null || startsOn == null || endsOn == null) {
            log.warn("GGC event skipped (missing id/title or date not yyyy-MM-dd): href={}, subject={}, start={}, end={}",
                    href, title, startText, endText);
            return null;
        }
        LocalTime[] times = EventTexts.times(text(item, "intime"));
        String homepage = EventTexts.clean(text(item, "homepage"));
        return new CollectedEvent(href, title, startsOn, endsOn, times[0], times[1],
                EventTexts.clean(text(item, "incost")), homepage == null ? href : homepage,
                null, null, region(item));
    }

    static String region(JsonNode item) {
        String area = text(item, "inarea");
        String subject = text(item, "subject");
        String homepage = text(item, "homepage");
        boolean siheung = (area != null && area.contains("시흥")) || (subject != null && subject.contains("시흥"))
                || (homepage != null && homepage.contains("siheung.go.kr"));
        return siheung ? EventRegion.SIHEUNG : EventRegion.GYEONGGI;
    }

    private static String text(JsonNode item, String key) {
        JsonNode value = item.get(key);
        return value == null || value.isNull() ? null : value.asString();
    }
}
