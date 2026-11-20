package com.siot.backend.translation.bill;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RUL-07 관련 안내: 세목(원문) → guide 코드. 어느 화면으로 연결할지는 프론트가 정한다.
 */
final class BillGuideLinks {

    private static final Map<String, String> BY_TAX_TYPE = new LinkedHashMap<>();

    static {
        BY_TAX_TYPE.put("자동차세", "CL-10");
        BY_TAX_TYPE.put("재산세", "CL-11");
    }

    private BillGuideLinks() {
    }

    /** 세목에 들어 있는 키워드 순서대로. 없으면 빈 목록 */
    static List<String> codes(String taxType) {
        if (taxType == null) {
            return List.of();
        }
        String compact = taxType.replaceAll("\\s", "");
        return BY_TAX_TYPE.entrySet().stream()
                .filter(entry -> compact.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();
    }
}
