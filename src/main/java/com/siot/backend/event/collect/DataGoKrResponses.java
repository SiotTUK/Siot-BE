package com.siot.backend.event.collect;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 공공데이터포털 응답 공통 처리 (TourAPI, 표준데이터).
 * 정상: {"response": {"header": {"resultCode": ...}, "body": {"items": ..., "totalCount": ...}}} (TourAPI) 또는
 * response 없이 header·body 가 최상위 {"header": ..., "body": ...} (표준데이터). 둘 다 읽는다.
 * 키 오류 등은 {"OpenAPI_ServiceResponse": {"cmmMsgHeader": {"errMsg": ...}}} 이나 XML 로 온다.
 */
final class DataGoKrResponses {

    /** TourAPI "0000", 표준데이터 "00" (정상), "03" (자료 없음) */
    private static final Set<String> OK_CODES = Set.of("0000", "00");
    private static final Set<String> NO_DATA_CODES = Set.of("03");

    private DataGoKrResponses() {
    }

    /** 응답 본문의 body. 오류 응답이면 {@link EventSourceException}. 자료 없음이면 null */
    static JsonNode body(String name, String response, JsonMapper jsonMapper) {
        JsonNode root;
        try {
            root = response == null ? null : jsonMapper.readTree(response);
        } catch (JacksonException e) {
            throw new EventSourceException(name + ": response is not JSON: " + abbreviate(response), e);
        }
        if (root == null || !root.isObject()) {
            throw new EventSourceException(name + ": empty response");
        }
        JsonNode serviceError = root.path("OpenAPI_ServiceResponse").path("cmmMsgHeader");
        if (!serviceError.isMissingNode()) {
            throw new EventSourceException(name + ": " + serviceError.path("errMsg").asString("")
                    + " " + serviceError.path("returnAuthMsg").asString(""));
        }
        JsonNode envelope = root.has("response") ? root.path("response") : root;
        JsonNode header = envelope.path("header");
        String code = header.path("resultCode").asString("");
        if (NO_DATA_CODES.contains(code)) {
            return null;
        }
        if (!OK_CODES.contains(code)) {
            throw new EventSourceException(name + ": resultCode=" + code + " " + header.path("resultMsg").asString(""));
        }
        JsonNode body = envelope.path("body");
        if (!body.isObject()) {
            throw new EventSourceException(name + ": unexpected response (no body)");
        }
        return body;
    }

    /** items 는 배열, {"item": 배열|객체}, 결과가 없으면 "" 로 온다 */
    static List<JsonNode> items(JsonNode body) {
        JsonNode items = body.path("items");
        JsonNode list = items.isArray() ? items : items.path("item");
        List<JsonNode> result = new ArrayList<>();
        if (list.isArray()) {
            list.forEach(result::add);
        } else if (list.isObject()) {
            result.add(list);
        }
        return result;
    }

    /** body 의 totalCount. 없거나 숫자가 아니면 -1 */
    static int totalCount(JsonNode body) {
        JsonNode total = body.path("totalCount");
        if (total.isNumber()) {
            return total.asInt();
        }
        if (!total.isString()) {
            return -1;
        }
        try {
            return Integer.parseInt(total.asString().strip());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * 다음 페이지를 부를지. totalCount 가 있으면 지금까지 넘긴 건수(page × pageSize)가 그보다 적을 때,
     * 없으면 이번 페이지가 꽉 찼을 때(받은 건수 >= pageSize).
     */
    static boolean hasNextPage(JsonNode body, int page, int pageSize, int itemCount) {
        int total = totalCount(body);
        if (total >= 0) {
            return (long) page * pageSize < total;
        }
        return itemCount >= pageSize;
    }

    static String text(JsonNode item, String key) {
        JsonNode value = item.get(key);
        return value == null || value.isNull() ? null : value.asString();
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "null";
        }
        String flat = text.replaceAll("\\s+", " ").strip();
        return flat.length() <= 200 ? flat : flat.substring(0, 200) + "…";
    }
}
