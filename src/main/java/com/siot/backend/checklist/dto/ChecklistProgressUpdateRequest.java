package com.siot.backend.checklist.dto;

import com.siot.backend.checklist.domain.ProgressStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "체크리스트 상태 변경 요청")
public record ChecklistProgressUpdateRequest(
        @Schema(description = "회차 키 (1회: '' 또는 생략, 세금: YYYY-MM, 생활변화: YYYY-MM-DD)", example = "")
        String periodKey,
        @Schema(description = "DONE(완료) / NOT_APPLICABLE(해당 없음). null 이면 선택 해제(TODO 복귀)", example = "DONE")
        ProgressStatus status
) {
}
