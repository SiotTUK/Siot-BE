package com.siot.backend.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(description = "관리자: 달력 공통 일정 등록·수정 (수정은 모든 값을 덮어쓴다)")
public record AdminScheduleRequest(
        @NotBlank
        @Size(max = 30)
        @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "kind 는 영문 대문자·숫자·밑줄입니다 (예: TAX_CAR).")
        @Schema(description = "일정 종류. 세금은 TAX_CAR / TAX_PROPERTY / TAX_RESIDENT (kind 에 TAX 가 들어가면 세금 상세·알림 대상)",
                example = "TAX_CAR")
        String kind,

        @Size(max = 50)
        @Schema(description = "연결할 항목 코드 (제목·상세 문구·문의처를 이 항목에서 가져온다). 없으면 null", nullable = true,
                example = "tax-car")
        String guideCode,

        @NotNull
        @Schema(description = "시작일", example = "2027-06-16")
        LocalDate startsOn,

        @Schema(description = "마감일 (없으면 시작일 하루). 시작일보다 빠를 수 없다", nullable = true, example = "2027-06-30")
        LocalDate endsOn,

        @Size(max = 1000)
        @Schema(description = "대상 설명 (번역되지 않고 그대로 보인다)", nullable = true)
        String targetDescription,

        @Size(max = 2000)
        @Pattern(regexp = "^$|^\\s*https?://\\S+\\s*$", message = "출처 링크는 http:// 또는 https:// 로 시작해야 합니다.")
        @Schema(description = "출처·납부 링크", nullable = true, example = "https://www.wetax.go.kr")
        String sourceUrl,

        @Schema(description = "달력·알림에 보일지. 생략하면 true. 지우는 대신 false 로 끈다 (자동 생성이 다시 만들지 않는다)",
                nullable = true, example = "true")
        Boolean active
) {
}
