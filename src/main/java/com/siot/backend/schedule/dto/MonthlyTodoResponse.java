package com.siot.backend.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "CAL-02 메인의 이번 달 할일. 이번 달에 남은 일정이 없으면 다음 달 일정")
public record MonthlyTodoResponse(
        @Schema(description = "보여 주는 달의 연도", example = "2026")
        int year,
        @Schema(description = "보여 주는 달 (1~12)", example = "12")
        int month,
        @Schema(description = "true 면 이번 달에 남은 일정이 없어 다음 달 일정을 보여 준다")
        boolean nextMonth,
        @Schema(description = "할일 목록 (마감 임박 순)")
        List<Item> items
) {

    @Schema(description = "할일 항목")
    public record Item(
            @Schema(description = "일정")
            ScheduleResponse schedule,
            @Schema(description = "마감(종료일, 없으면 시작일)까지 남은 일수. 0 = 오늘 마감", example = "5")
            long dDay
    ) {
    }
}
