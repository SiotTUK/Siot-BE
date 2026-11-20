package com.siot.backend.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** EVE-05 추천 행사 하나와 추천 이유 */
@Schema(description = "EVE-05 추천 행사")
public record EventRecommendationResponse(
        @Schema(description = "행사 카드") EventSummaryResponse event,
        @Schema(description = "추천 이유 (앞쪽이 더 강한 이유). FAMILY_KIDS=자녀 동반(온보딩 거주 정보 '자녀'), "
                + "KOREAN_CLASS=한국어 교육(온보딩 '한국어 공부'), NEWCOMER=새로 온 사람(온보딩 '처음 입국'), "
                + "MULTICULTURAL=다문화 행사, SIHEUNG=시흥시 행사",
                example = "[\"FAMILY_KIDS\", \"SIHEUNG\"]")
        List<String> reasons) {
}
