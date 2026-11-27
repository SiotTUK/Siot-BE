package com.siot.backend.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "관리자: 공통 세금 일정 자동 생성 결과")
public record TaxScheduleGenerateResponse(
        @Schema(description = "연도", example = "2027") int year,
        @Schema(description = "새로 만든 일정 (이미 있던 회차는 빠진다)") List<AdminScheduleResponse> created,
        @Schema(description = "마감일을 공식 공휴일 자료(천문연 특일 정보)로 계산했는지. false 면 주말·양력 고정 공휴일만 반영했으므로 "
                + "설날·추석·대체·임시공휴일에 걸린 마감일을 확인해 고친다") boolean officialHolidays
) {
}
