package com.siot.backend.checklist.dto;

import com.siot.backend.checklist.domain.ProgressStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

@Schema(description = "체크리스트 상태 변경 요청")
public record ChecklistProgressUpdateRequest(
        @Pattern(regexp = "^$|^[0-9]{4}-(0[1-9]|1[0-2])(-(0[1-9]|[12][0-9]|3[01]))?$",
                message = "periodKey 는 빈 값(1회), YYYY-MM(세금 회차), YYYY-MM-DD(생활 변화 기준일) 중 하나입니다.")
        @Schema(description = "회차 키 (1회: '' 또는 생략, 세금: YYYY-MM, 생활변화: YYYY-MM-DD)", example = "")
        String periodKey,
        @Schema(description = "DONE(완료) / NOT_APPLICABLE(해당 없음). null 이면 선택 해제(TODO 복귀)", example = "DONE")
        ProgressStatus status
) {
}
