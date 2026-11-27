package com.siot.backend.event.collect;

import com.siot.backend.event.domain.EventCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 한국관광공사 TourAPI 행사정보조회 searchFestival2 (키: EVENT_API_KEY).
 * <ul>
 *     <li>시흥만: 요청 파라미터 lDongRegnCd=41, lDongSignguCd=390 (법정동 코드). 응답에서도 다시 확인한다.
 *         addr1 의 "시흥"으로는 거르지 않는다 (서울 금천구 시흥동)</li>
 *     <li>eventStartDate = 오늘 - lookback-days 로 넓게 받고, 종료일 >= 오늘인 것만 저장한다</li>
 *     <li>external_id = contentid, 장소 = addr1</li>
 *     <li>원문 링크·설명은 목록에 없어서, 저장할 행사마다 detailCommon2 를 contentId 로 한 번 더 부른다:
 *         homepage(HTML 링크가 섞여 옴) → 원문 링크, overview(&lt;br&gt; 등 태그) → 설명.
 *         상세 조회가 실패하면 그 행사는 목록 값만으로 저장한다</li>
 * </ul>
 */
@Slf4j
public class TourApiEventSource implements EventSource {

    private static final DateTimeFormatter BASIC = DateTimeFormatter.BASIC_ISO_DATE;

    private final RestClient restClient;
    private final EventProperties props;
    private final JsonMapper jsonMapper;

    public TourApiEventSource(RestClient restClient, EventProperties props, JsonMapper jsonMapper) {
        this.restClient = restClient;
        this.props = props;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public EventSourceType type() {
        return EventSourceType.TOURAPI;
    }

    @Override
    public FetchResult fetch(LocalDate today) {
        if (!props.hasApiKey()) {
            throw new EventSourceException("TOURAPI: EVENT_API_KEY is not configured");
        }
        EventProperties.TourApi api = props.tourapi();
        String from = today.minusDays(api.lookbackDays()).format(BASIC);
        List<CollectedEvent> events = new ArrayList<>();
        int fetched = 0;
        for (int page = 1; page <= api.maxPages(); page++) {
            JsonNode body = DataGoKrResponses.body("TOURAPI", request(api, from, page), jsonMapper);
            if (body == null) {
                break;
            }
            List<JsonNode> items = DataGoKrResponses.items(body);
            if (items.isEmpty()) {
                break;
            }
            fetched += items.size();
            for (JsonNode item : items) {
                CollectedEvent event = parse(item, api);
                if (event != null && !event.endsOn().isBefore(today)) {
                    events.add(withDetail(event, api));
                }
            }
            if (!DataGoKrResponses.hasNextPage(body, page, api.pageSize(), items.size())) {
                break;
            }
        }
        return new FetchResult(fetched, events);
    }

    private String request(EventProperties.TourApi api, String from, int page) {
        try {
            return restClient.get()
                    .uri(api.baseUrl() + "?serviceKey={key}&MobileOS=ETC&MobileApp=siot&_type=json"
                                    + "&eventStartDate={from}&lDongRegnCd={regn}&lDongSignguCd={signgu}"
                                    + "&numOfRows={rows}&pageNo={page}",
                            props.apiKey(), from, api.regionCode(), api.districtCode(), api.pageSize(), page)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw new EventSourceException("TOURAPI page " + page + " request failed: " + e.getMessage(), e);
        }
    }

    /** 상세(원문 링크·설명)를 붙인다. 실패하거나 값이 없으면 목록 값 그대로 */
    CollectedEvent withDetail(CollectedEvent event, EventProperties.TourApi api) {
        JsonNode item;
        try {
            JsonNode body = DataGoKrResponses.body("TOURAPI detail", requestDetail(api, event.externalId()),
                    jsonMapper);
            List<JsonNode> items = body == null ? List.of() : DataGoKrResponses.items(body);
            if (items.isEmpty()) {
                return event;
            }
            item = items.getFirst();
        } catch (RuntimeException e) {
            log.warn("TOURAPI detail skipped for contentid={} ({}): {}", event.externalId(), event.title(),
                    e.getMessage());
            return event;
        }
        String sourceUrl = EventTexts.firstUrl(DataGoKrResponses.text(item, "homepage"));
        String description = EventTexts.htmlToText(DataGoKrResponses.text(item, "overview"));
        String thumbnailUrl = image(item);
        return new CollectedEvent(event.externalId(), event.title(), event.startsOn(), event.endsOn(),
                event.startsAt(), event.endsAt(), event.priceInfo(),
                sourceUrl != null ? sourceUrl : event.sourceUrl(), event.venue(),
                description != null ? description : event.description(), event.regionCode(), event.category(),
                thumbnailUrl != null ? thumbnailUrl : event.thumbnailUrl());
    }

    private String requestDetail(EventProperties.TourApi api, String contentId) {
        try {
            return restClient.get()
                    .uri(api.detailUrl() + "?serviceKey={key}&MobileOS=ETC&MobileApp=siot&_type=json"
                            + "&contentId={id}", props.apiKey(), contentId)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw new EventSourceException("TOURAPI detail " + contentId + " request failed: " + e.getMessage(), e);
        }
    }

    /** 시흥(법정동 코드)이 아니거나, 필수 값이 없거나 날짜 형식(yyyyMMdd)이 다르면 null */
    CollectedEvent parse(JsonNode item, EventProperties.TourApi api) {
        if (!api.regionCode().equals(DataGoKrResponses.text(item, "lDongRegnCd"))
                || !api.districtCode().equals(DataGoKrResponses.text(item, "lDongSignguCd"))) {
            return null;
        }
        String id = EventTexts.clean(DataGoKrResponses.text(item, "contentid"));
        String title = EventTexts.title(DataGoKrResponses.text(item, "title"));
        String startText = DataGoKrResponses.text(item, "eventstartdate");
        String endText = DataGoKrResponses.text(item, "eventenddate");
        LocalDate startsOn = EventTexts.basicDate(startText);
        LocalDate endsOn = EventTexts.clean(endText) == null ? startsOn : EventTexts.basicDate(endText);
        if (id == null || title == null || startsOn == null || endsOn == null) {
            log.warn("TOURAPI event skipped (missing id/title or date not yyyyMMdd): contentid={}, title={}, start={}, end={}",
                    id, title, startText, endText);
            return null;
        }
        return new CollectedEvent(id, title, startsOn, endsOn, null, null, null, null,
                EventTexts.clean(DataGoKrResponses.text(item, "addr1")), null, EventRegion.SIHEUNG,
                EventCategory.fromTourApi(DataGoKrResponses.text(item, "lclsSystm2"), DataGoKrResponses.text(item, "cat2")),
                image(item));
    }

    /** 대표 사진 firstimage(원본), 없으면 firstimage2(작은 사진) (BE #19) */
    static String image(JsonNode item) {
        String first = EventTexts.imageUrl(DataGoKrResponses.text(item, "firstimage"));
        return first != null ? first : EventTexts.imageUrl(DataGoKrResponses.text(item, "firstimage2"));
    }
}
