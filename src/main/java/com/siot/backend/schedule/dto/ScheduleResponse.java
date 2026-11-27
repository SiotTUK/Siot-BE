package com.siot.backend.schedule.dto;

import com.siot.backend.global.config.ClockConfig;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Schema(description = "생활 달력 일정 응답")
public record ScheduleResponse(
        @Schema(description = "일정 ID (개인 일정인 경우 null)", example = "1")
        Long scheduleId,
        @Schema(description = "관련 가이드 항목 ID")
        Long guideId,
        @Schema(description = "일정 종류 (TAX, TAX_CAR, TAX_PROPERTY, STAY_EXPIRY, STAY_EXTENSION_START 등)", example = "TAX")
        String kind,
        @Schema(description = "일정 제목", example = "주민세 개인분 납부")
        String title,
        @Schema(description = "시작일", example = "2026-08-16")
        LocalDate startsOn,
        @Schema(description = "종료일", example = "2026-08-31")
        LocalDate endsOn,
        @Schema(description = "대상 설명")
        String targetDescription,
        @Schema(description = "출처 링크")
        String sourceUrl,
        @Schema(description = "개인 맞춤 일정 여부 (체류만료 등)")
        boolean personal,
        @Schema(description = "강조 표시 여부. 체류 만료 30일 전부터 STAY_EXPIRY·STAY_EXTENSION_START 가 true (CAL-04)")
        boolean highlighted,
        @Schema(description = "마감(종료일, 없으면 시작일)까지 남은 일수 (한국 시간 오늘 기준). 음수: 지남, 0: 오늘, 양수: 남음. "
                + "날짜가 없으면 null", nullable = true, example = "5")
        Long dDay,
        @Schema(description = "CAL-03 세금 상세(GET /api/schedules/taxes/{id})를 열 때 쓰는 공통 세금 일정 ID (BE #29). "
                + "공통 세금 일정은 자기 scheduleId, 개인 자동차세·재산세(TAX_CAR·TAX_PROPERTY)는 같은 종류이면서 그 달에 걸친 "
                + "공통 일정 (6월·12월처럼 납부 시기가 여럿이면 그 회차). 대응하는 공통 일정이 없거나 세금 일정이 아니면 null",
                nullable = true, example = "10")
        Long taxScheduleId
) {

    /** D-day 를 오늘(한국 시간) 기준으로 계산한다 */
    public ScheduleResponse(Long scheduleId, Long guideId, String kind, String title, LocalDate startsOn, LocalDate endsOn,
                            String targetDescription, String sourceUrl, boolean personal, boolean highlighted) {
        this(scheduleId, guideId, kind, title, startsOn, endsOn, targetDescription, sourceUrl, personal, highlighted,
                dDay(startsOn, endsOn, LocalDate.now(ClockConfig.KST)), null);
    }

    /** 세금 상세로 열 공통 일정 ID 를 붙인 사본 */
    public ScheduleResponse withTaxScheduleId(Long taxScheduleId) {
        return new ScheduleResponse(scheduleId, guideId, kind, title, startsOn, endsOn, targetDescription, sourceUrl,
                personal, highlighted, dDay, taxScheduleId);
    }

    /** 강조하지 않는 일정 */
    public ScheduleResponse(Long scheduleId, Long guideId, String kind, String title, LocalDate startsOn, LocalDate endsOn,
                            String targetDescription, String sourceUrl, boolean personal) {
        this(scheduleId, guideId, kind, title, startsOn, endsOn, targetDescription, sourceUrl, personal, false);
    }

    /** 마감(종료일, 없으면 시작일)까지 남은 일수. 날짜가 없으면 null */
    public static Long dDay(LocalDate startsOn, LocalDate endsOn, LocalDate today) {
        LocalDate due = endsOn != null ? endsOn : startsOn;
        return due == null ? null : ChronoUnit.DAYS.between(today, due);
    }
}
