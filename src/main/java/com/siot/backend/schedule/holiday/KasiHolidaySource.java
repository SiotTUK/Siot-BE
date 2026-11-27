package com.siot.backend.schedule.holiday;

import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 한국천문연구원 특일 정보 getRestDeInfo (공공데이터포털). 설날·추석, 대체공휴일, 임시공휴일까지 내려 준다.
 * 한 해를 한 번에 받는다. 그해 1월 1일(신정)이 없으면 아직 발표되지 않은 해로 보고
 * {@link HolidayUnavailableException} (빈 응답을 "공휴일 없음" 으로 잘못 믿지 않도록).
 */
public class KasiHolidaySource implements HolidaySource {

    private static final DateTimeFormatter LOCDATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final RestClient restClient;
    private final HolidayProperties properties;
    private final JsonMapper jsonMapper;

    public KasiHolidaySource(RestClient restClient, HolidayProperties properties, JsonMapper jsonMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public Set<LocalDate> holidays(int year) {
        if (!properties.configured()) {
            throw new HolidayUnavailableException("siot.holiday.api-key is not configured");
        }
        String response;
        try {
            response = restClient.get()
                    .uri(properties.baseUrl() + "?serviceKey={key}&solYear={year}&numOfRows=100&_type=json",
                            properties.apiKey(), year)
                    .retrieve()
                    .body(String.class);
        } catch (RuntimeException e) {
            throw new HolidayUnavailableException("KASI holiday request failed: " + e.getMessage(), e);
        }
        Set<LocalDate> holidays = parse(response, jsonMapper);
        if (!holidays.contains(LocalDate.of(year, 1, 1))) {
            throw new HolidayUnavailableException("KASI has no holiday data for " + year + " yet");
        }
        return holidays;
    }

    /** {"response":{"header":{"resultCode":"00"},"body":{"items":{"item":[…]|{…}}|""}}} 에서 isHoliday=Y 인 날짜 */
    static Set<LocalDate> parse(String response, JsonMapper jsonMapper) {
        JsonNode root;
        try {
            root = response == null ? null : jsonMapper.readTree(response);
        } catch (JacksonException e) {
            // 키 오류는 XML(OpenAPI_ServiceResponse)로 온다
            throw new HolidayUnavailableException("KASI response is not JSON: " + abbreviate(response), e);
        }
        if (root == null || !root.isObject()) {
            throw new HolidayUnavailableException("KASI response is empty");
        }
        JsonNode serviceError = root.path("OpenAPI_ServiceResponse").path("cmmMsgHeader");
        if (!serviceError.isMissingNode()) {
            throw new HolidayUnavailableException("KASI error: " + serviceError.path("returnAuthMsg").asString(""));
        }
        JsonNode envelope = root.path("response");
        String code = envelope.path("header").path("resultCode").asString("");
        if (!"00".equals(code)) {
            throw new HolidayUnavailableException("KASI resultCode=" + code + " "
                    + envelope.path("header").path("resultMsg").asString(""));
        }
        JsonNode items = envelope.path("body").path("items");
        JsonNode list = items.isArray() ? items : items.path("item");
        List<JsonNode> rows = new ArrayList<>();
        if (list.isArray()) {
            list.forEach(rows::add);
        } else if (list.isObject()) {
            rows.add(list);
        }
        Set<LocalDate> holidays = new HashSet<>();
        for (JsonNode row : rows) {
            if (!"Y".equals(row.path("isHoliday").asString(""))) {
                continue;
            }
            String locdate = row.path("locdate").isNumber()
                    ? String.valueOf(row.path("locdate").asLong()) : row.path("locdate").asString("");
            try {
                holidays.add(LocalDate.parse(locdate, LOCDATE));
            } catch (RuntimeException e) {
                throw new HolidayUnavailableException("KASI locdate is not a date: " + locdate, e);
            }
        }
        return holidays;
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "null";
        }
        return text.length() <= 200 ? text : text.substring(0, 200) + "…";
    }
}
