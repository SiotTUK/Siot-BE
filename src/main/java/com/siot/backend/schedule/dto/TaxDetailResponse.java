package com.siot.backend.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "세금 상세 일정 및 납부 안내 응답 (CAL-03)")
public record TaxDetailResponse(
        @Schema(description = "일정 ID", example = "10")
        Long scheduleId,

        @Schema(description = "가이드 ID", example = "5")
        Long guideId,

        @Schema(description = "세금 종류 (TAX_CAR, TAX_PROPERTY, TAX_RESIDENT 등)", example = "TAX_CAR")
        String kind,

        @Schema(description = "세금 명칭", example = "자동차세 1기분 납부")
        String title,

        @Schema(description = "납부 시작일", example = "2026-06-16")
        LocalDate startsOn,

        @Schema(description = "납부 마감일", example = "2026-06-30")
        LocalDate endsOn,

        @Schema(description = "과세 대상 설명", example = "6월 1일 기준 시흥시 등록 자동차 소유자")
        String targetDescription,

        @Schema(description = "납부 사이트 링크", example = "https://www.wetax.go.kr")
        String sourceUrl,

        @Schema(description = "요약")
        String summary,

        @Schema(description = "상세 안내 내용")
        String content,

        @Schema(description = "납부 장소 및 방법")
        String place,

        @Schema(description = "필요 서류")
        List<String> docs,

        @Schema(description = "세율 및 수수료 안내")
        String fee,

        @Schema(description = "근거 법령")
        String law,

        @Schema(description = "기한 경과 시 불이익 (가산세 등)")
        String penalty,

        @Schema(description = "문의처 (요청 언어 문구, BE #29). 연결된 항목(guideId)의 언어별 문의처, 없으면 항목의 문의처. "
                + "항목이 연결되지 않았거나 등록된 문의처가 없으면 null", nullable = true,
                example = "Siheung City Tax Management Division 031-310-2093")
        String contact,

        @Schema(description = "출처 안내책자 쪽수", example = "p.45")
        String sourcePages,

        @Schema(description = "납부 마감일까지 남은 일수 (한국 시간 오늘 기준). 음수: 지남, 0: 오늘", nullable = true, example = "5")
        Long dDay
) {
}
