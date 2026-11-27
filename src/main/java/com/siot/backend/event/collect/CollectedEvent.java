package com.siot.backend.event.collect;

import com.siot.backend.event.domain.EventCategory;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 출처에서 받은 행사 하나 (저장 전). 글은 한국어 원문.
 *
 * @param externalId   출처 안에서 행사를 가리키는 ID (event.external_id)
 * @param title        행사명 (ko 번역 행 제목)
 * @param startsOn     시작일 (필수)
 * @param endsOn       종료일 (없으면 시작일)
 * @param startsAt     시작 시각 (모르면 null)
 * @param endsAt       종료 시각 (모르면 null)
 * @param priceInfo    가격 안내 (원문 그대로)
 * @param sourceUrl    원문 링크
 * @param venue        장소 (ko)
 * @param description  상세 설명 (ko)
 * @param regionCode   {@link EventRegion}
 * @param category     출처 분류를 옮긴 카테고리 (EVE-10). null 이면 EVENT
 * @param thumbnailUrl 행사 사진 URL (http/https, BE #19). 없으면 null
 */
public record CollectedEvent(String externalId, String title, LocalDate startsOn, LocalDate endsOn,
                             LocalTime startsAt, LocalTime endsAt, String priceInfo, String sourceUrl,
                             String venue, String description, String regionCode, EventCategory category,
                             String thumbnailUrl) {

    public CollectedEvent {
        if (category == null) {
            category = EventCategory.EVENT;
        }
    }

    /** 사진이 없는 행사 */
    public CollectedEvent(String externalId, String title, LocalDate startsOn, LocalDate endsOn,
                          LocalTime startsAt, LocalTime endsAt, String priceInfo, String sourceUrl,
                          String venue, String description, String regionCode, EventCategory category) {
        this(externalId, title, startsOn, endsOn, startsAt, endsAt, priceInfo, sourceUrl, venue, description,
                regionCode, category, null);
    }

    /** 카테고리를 모르는 행사 (EVENT) */
    public CollectedEvent(String externalId, String title, LocalDate startsOn, LocalDate endsOn,
                          LocalTime startsAt, LocalTime endsAt, String priceInfo, String sourceUrl,
                          String venue, String description, String regionCode) {
        this(externalId, title, startsOn, endsOn, startsAt, endsAt, priceInfo, sourceUrl, venue, description,
                regionCode, EventCategory.EVENT);
    }
}
