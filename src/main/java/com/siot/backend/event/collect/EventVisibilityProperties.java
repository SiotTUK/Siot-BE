package com.siot.backend.event.collect;

import com.siot.backend.event.domain.Event;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Set;

/**
 * 사용자에게 보여 주는 행사 지역 (siot.event.public-regions, 환경 변수 EVENT_PUBLIC_REGIONS).
 * 수집과 관리자 API 는 이 설정과 상관없이 모든 지역을 다룬다.
 *
 * @param publicRegions {@link EventRegion} 값 (쉼표로 여러 개). 비었거나 모르는 값이면 서버가 시작하지 않는다
 */
@Validated
@ConfigurationProperties(prefix = "siot.event")
public record EventVisibilityProperties(
        @NotEmpty Set<@Pattern(regexp = EventRegion.SIHEUNG + "|" + EventRegion.GYEONGGI) String> publicRegions
) {

    /** 사용자에게 보여 주는 지역의 행사인지 (삭제 여부는 보지 않는다). 지역이 없는 행사는 false */
    public boolean inPublicRegion(Event event) {
        return event.getRegionCode() != null && publicRegions.contains(event.getRegionCode());
    }

    /** 사용자에게 보여 주는 행사인지: 지우지 않았고 공개 지역 */
    public boolean isVisible(Event event) {
        return !event.isDeleted() && inPublicRegion(event);
    }
}
