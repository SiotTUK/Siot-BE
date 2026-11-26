package com.siot.backend.event.collect;

import com.siot.backend.event.domain.EventCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 전국문화축제표준데이터 Open API (공공데이터포털, 키: EVENT_API_KEY).
 * GET {base-url}?serviceKey&pageNo&numOfRows&type=json. 항목 키는 공공데이터 표준 이름을 따른다
 * (fstvlNm 축제명, opar 개최장소, fstvlStartDate/fstvlEndDate yyyy-MM-dd, fstvlCo 축제내용,
 * homepageUrl 홈페이지주소, rdnmadr 소재지도로명주소, lnmadr 소재지지번주소).
 * <ul>
 *     <li>시흥만: 도로명 또는 지번 주소가 "경기도 시흥시"로 시작</li>
 *     <li>원본 ID 가 없어 external_id = 축제명 + "|" + 시작일</li>
 *     <li>장소(opar)·내용(fstvlCo)은 여러 개를 "+" 로 이어 보낸다 → ", " 로 바꿔 저장</li>
 *     <li>홈페이지는 "www.…" 처럼 http 없이 오기도 한다 → https:// 를 붙인다. 주소 꼴이 아니면 버린다</li>
 *     <li>응답은 response 없이 header·body 가 최상위로 온다 ({@link DataGoKrResponses})</li>
 * </ul>
 */
@Slf4j
public class FestivalStandardEventSource implements EventSource {

    /** opar·fstvlCo 의 항목 구분자 "+" (앞뒤 공백 포함) */
    private static final Pattern PLUS = Pattern.compile("\\s*\\+\\s*");
    /** 스킴 없는 웹 주소 ("www.instagram.com/sgfestival", "siheung.go.kr") */
    private static final Pattern BARE_HOST = Pattern.compile(
            "^[\\p{L}\\p{N}-]+(\\.[\\p{L}\\p{N}-]+)+(:\\d+)?([/?#]\\S*)?$");

    private final RestClient restClient;
    private final EventProperties props;
    private final JsonMapper jsonMapper;

    public FestivalStandardEventSource(RestClient restClient, EventProperties props, JsonMapper jsonMapper) {
        this.restClient = restClient;
        this.props = props;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public EventSourceType type() {
        return EventSourceType.FESTIVAL_STD;
    }

    @Override
    public FetchResult fetch(LocalDate today) {
        if (!props.hasApiKey()) {
            throw new EventSourceException("FESTIVAL_STD: EVENT_API_KEY is not configured");
        }
        EventProperties.Festival api = props.festival();
        List<CollectedEvent> events = new ArrayList<>();
        int fetched = 0;
        for (int page = 1; page <= api.maxPages(); page++) {
            JsonNode body = DataGoKrResponses.body("FESTIVAL_STD", request(api, page), jsonMapper);
            if (body == null) {
                break;
            }
            List<JsonNode> items = DataGoKrResponses.items(body);
            if (items.isEmpty()) {
                break;
            }
            fetched += items.size();
            for (JsonNode item : items) {
                CollectedEvent event = parse(item, api.addressPrefix());
                if (event != null && !event.endsOn().isBefore(today)) {
                    events.add(event);
                }
            }
            if (!DataGoKrResponses.hasNextPage(body, page, api.pageSize(), items.size())) {
                break;
            }
        }
        return new FetchResult(fetched, events);
    }

    private String request(EventProperties.Festival api, int page) {
        try {
            return restClient.get()
                    .uri(api.baseUrl() + "?serviceKey={key}&pageNo={page}&numOfRows={rows}&type=json",
                            props.apiKey(), page, api.pageSize())
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw new EventSourceException("FESTIVAL_STD page " + page + " request failed: " + e.getMessage(), e);
        }
    }

    /** 시흥 주소가 아니거나, 필수 값이 없거나 날짜 형식(yyyy-MM-dd)이 다르면 null */
    CollectedEvent parse(JsonNode item, String addressPrefix) {
        String roadAddress = EventTexts.clean(DataGoKrResponses.text(item, "rdnmadr"));
        String lotAddress = EventTexts.clean(DataGoKrResponses.text(item, "lnmadr"));
        if (!startsWith(roadAddress, addressPrefix) && !startsWith(lotAddress, addressPrefix)) {
            return null;
        }
        String title = EventTexts.title(DataGoKrResponses.text(item, "fstvlNm"));
        String startText = DataGoKrResponses.text(item, "fstvlStartDate");
        String endText = DataGoKrResponses.text(item, "fstvlEndDate");
        LocalDate startsOn = EventTexts.isoDate(startText);
        LocalDate endsOn = EventTexts.clean(endText) == null ? startsOn : EventTexts.isoDate(endText);
        if (title == null || startsOn == null || endsOn == null) {
            log.warn("FESTIVAL_STD event skipped (missing name or date not yyyy-MM-dd): name={}, start={}, end={}",
                    title, startText, endText);
            return null;
        }
        return new CollectedEvent(title + "|" + startsOn, title, startsOn, endsOn, null, null, null,
                homepage(DataGoKrResponses.text(item, "homepageUrl")),
                joinItems(DataGoKrResponses.text(item, "opar")),
                joinItems(DataGoKrResponses.text(item, "fstvlCo")), EventRegion.SIHEUNG,
                EventCategory.FESTIVAL); // 축제 데이터셋이라 모두 축제
    }

    /** "금남로+충장로" → "금남로, 충장로". 빈 항목은 뺀다 */
    static String joinItems(String text) {
        String cleaned = EventTexts.clean(text);
        if (cleaned == null) {
            return null;
        }
        String joined = String.join(", ", PLUS.splitAsStream(cleaned).filter(part -> !part.isBlank()).toList());
        return joined.isEmpty() ? null : joined;
    }

    /** http(s):// 가 있으면 그대로, "www.…" 처럼 없으면 https:// 를 붙인다. 웹 주소 꼴이 아니면 null */
    static String homepage(String text) {
        String cleaned = EventTexts.clean(text);
        if (cleaned == null) {
            return null;
        }
        String lower = cleaned.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return cleaned;
        }
        if (lower.startsWith("//")) {
            return "https:" + cleaned;
        }
        return BARE_HOST.matcher(cleaned).matches() ? "https://" + cleaned : null;
    }

    private static boolean startsWith(String address, String prefix) {
        return address != null && address.startsWith(prefix);
    }
}
