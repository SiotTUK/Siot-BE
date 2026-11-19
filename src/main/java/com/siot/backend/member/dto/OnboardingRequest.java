package com.siot.backend.member.dto;

import com.siot.backend.member.domain.Purpose;
import com.siot.backend.member.domain.Residence;
import com.siot.backend.member.domain.UserType;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/**
 * 온보딩 답 저장 (F02~F06). 내 정보 화면에서 고칠 때도 같은 요청을 쓰며, 항상 5개 답 전체를 덮어쓴다.
 * 선택 항목을 비우려면 null(거주 정보는 빈 배열)을 보낸다.
 */
@Schema(description = "온보딩 답 (F02~F06). 전체를 덮어쓴다 — 보내지 않은 선택 항목은 지워진다")
public record OnboardingRequest(
        @Schema(description = "F02 사용자 유형 (필수). NEW = 처음 입국, LIVING = 원래 거주", example = "NEW")
        @NotNull UserType userType,
        @Schema(description = "F03 입국 날짜 (선택)", nullable = true, example = "2026-11-01")
        LocalDate entryDate,
        @Schema(description = "F04 체류 목적 (선택). 건너뛰면 null → 체크리스트는 OTHER 로 계산", nullable = true, example = "STUDY")
        Purpose purpose,
        @ArraySchema(arraySchema = @Schema(description = "F05 거주 정보 (복수 선택, 없으면 빈 배열 또는 생략)", nullable = true),
                schema = @Schema(implementation = Residence.class))
        List<@NotNull Residence> residences,
        @Schema(description = "F06 체류 만료일 (선택)", nullable = true, example = "2027-11-01")
        LocalDate stayExpiry
) {
}
