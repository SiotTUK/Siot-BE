package com.siot.backend.event.collect;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 출처에서 받은 행사 하나 (저장 전). 글은 한국어 원문.
 *
 * @param externalId  출처 안에서 행사를 가리키는 ID (event.external_id)
 * @param title       행사명 (ko 번역 행 제목)
 * @param startsOn    시작일 (필수)
 * @param endsOn      종료일 (없으면 시작일)
 * @param startsAt    시작 시각 (모르면 null)
 * @param endsAt      종료 시각 (모르면 null)
 * @param priceInfo   가격 안내 (원문 그대로)
 * @param sourceUrl   원문 링크
 * @param venue       장소 (ko)
 * @param description 상세 설명 (ko)
 * @param regionCode  {@link EventRegion}
 */
public record CollectedEvent(String externalId, String title, LocalDate startsOn, LocalDate endsOn,
                             LocalTime startsAt, LocalTime endsAt, String priceInfo, String sourceUrl,
                             String venue, String description, String regionCode) {
}
