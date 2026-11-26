package com.siot.backend.event.dto;

import com.siot.backend.event.domain.EventCollectionRun;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/** 출처 하나의 수집 실행 결과 (event_collection_run) */
@Schema(description = "행사 수집 실행 결과 (출처 하나)")
public record EventCollectionRunResponse(
        @Schema(description = "실행 ID", example = "31") Long runId,
        @Schema(description = "출처", allowableValues = {"GGC", "FESTIVAL_STD", "TOURAPI"}, example = "GGC") String source,
        @Schema(description = "상태", allowableValues = {"RUNNING", "SUCCEEDED", "FAILED"}, example = "SUCCEEDED")
        String status,
        @Schema(description = "시작 시각") OffsetDateTime startedAt,
        @Schema(description = "끝난 시각", nullable = true) OffsetDateTime finishedAt,
        @Schema(description = "출처에서 받은 행사 수 (거르기 전)", example = "120") int fetchedCount,
        @Schema(description = "새로 만들거나 내용이 바뀐 행사 수", example = "3") int upsertedCount,
        @Schema(description = "실패 이유", nullable = true) String error) {

    public static EventCollectionRunResponse from(EventCollectionRun run) {
        return new EventCollectionRunResponse(run.getId(), run.getSource(), run.getStatus(), run.getStartedAt(),
                run.getFinishedAt(), run.getFetchedCount(), run.getUpsertedCount(), run.getError());
    }
}
