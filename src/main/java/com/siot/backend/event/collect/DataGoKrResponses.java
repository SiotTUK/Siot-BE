package com.siot.backend.event.collect;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 공공데이터포털 응답 공통 처리 (TourAPI, 표준데이터).
 * 정상: {"response": {"header": {"resultCode": ...}, "body": {"items": ..., "totalCount": ...}}}.
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
        JsonNode header = root.path("response").path("header");
        String code = header.path("resultCode").asString("");
        if (NO_DATA_CODES.contains(code)) {
            return null;
        }
        if (!OK_CODES.contains(code)) {
            throw new EventSourceException(name + ": resultCode=" + code + " " + header.path("resultMsg").asString(""));
        }
        JsonNode body = root.path("response").path("body");
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

    static int totalCount(JsonNode body) {
        JsonNode total = body.path("totalCount");
        if (total.isNumber()) {
            return total.asInt();
        }
        try {
            return Integer.parseInt(total.asString("0").strip());
        } catch (NumberFormatException e) {
            return 0;
        }
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
