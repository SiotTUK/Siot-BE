package com.siot.backend.event.collect;

import java.util.List;

/**
 * @param fetchedCount 출처에서 받은 행사 수 (거르기 전)
 * @param events       저장할 행사 (종료일 >= 오늘, 지역 조건 통과)
 */
public record FetchResult(int fetchedCount, List<CollectedEvent> events) {
}
