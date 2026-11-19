package com.siot.backend.member.dto;

import com.siot.backend.global.common.SupportedLanguages;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

/** 내 정보 일부 수정 (PATCH). 보내지 않은(null) 항목은 그대로 둔다. */
@Schema(description = "내 정보 수정. 보내지 않은(null) 항목은 바꾸지 않는다")
public record MemberUpdateRequest(
        @Schema(description = "화면 언어. 비밀번호 재설정·알림 메일도 이 언어로 간다",
                allowableValues = {"ko", "en", "zh", "vi"}, nullable = true, example = "en")
        @Pattern(regexp = SupportedLanguages.PATTERN) String language
) {
}
