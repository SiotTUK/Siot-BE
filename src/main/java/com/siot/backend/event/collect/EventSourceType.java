package com.siot.backend.event.collect;

/**
 * 행사 출처 (event.external_source, event_collection_run.source).
 * 선언 순서대로 수집한다: 출처 간 같은 행사는 먼저 저장된 것을 남기고 뒤 출처는 빈 칸(장소·설명)만 채우므로,
 * 가격·시각·공식 링크가 있는 지지씨 → 장소 이름·설명이 있는 축제표준데이터 → 주소만 있는 TourAPI 순.
 */
public enum EventSourceType {
    GGC,
    FESTIVAL_STD,
    TOURAPI
}
