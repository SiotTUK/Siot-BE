package com.siot.backend.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** M-06: 탈퇴 전 비밀번호 재확인 */
@Schema(description = "M-06 회원 탈퇴 (비밀번호 재확인)")
public record WithdrawalRequest(
        @Schema(description = "현재 비밀번호", example = "abcd1234")
        @NotBlank @Size(max = 64) String password) {
}
