package com.siot.backend.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** M-06: 탈퇴 전 비밀번호 재확인 */
public record WithdrawalRequest(@NotBlank @Size(max = 64) String password) {
}
