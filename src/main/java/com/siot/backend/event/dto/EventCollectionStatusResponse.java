package com.siot.backend.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

/** EVE-10 최종 갱신 시각: 출처별 마지막 성공 시각과 마지막 실행 결과 */
@Schema(description = "EVE-10 행사 수집 상태")
public record EventCollectionStatusResponse(
        @Schema(description = "가장 최근에 성공한 수집 시각 (화면의 \"최종 갱신\"). 성공한 적이 없으면 null", nullable = true)
        OffsetDateTime lastUpdatedAt,
        @Schema(description = "출처별 상태") List<SourceStatus> sources) {

    @Schema(description = "출처 하나의 수집 상태")
    public record SourceStatus(
            @Schema(description = "출처", allowableValues = {"GGC", "FESTIVAL_STD", "TOURAPI"}, example = "GGC")
            String source,
            @Schema(description = "마지막 성공 시각", nullable = true) OffsetDateTime lastSucceededAt,
            @Schema(description = "마지막 실행 (성공·실패 무관)", nullable = true) EventCollectionRunResponse lastRun) {
    }
}
