package com.siot.backend.checklist.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "#14 출처 (공식 페이지 이름·링크)")
public record RuleSourceRequest(
        @NotBlank @Size(max = 255)
        @Schema(description = "페이지 이름 (한국어)", example = "종량제 봉투 배출 안내 (시흥시)") String name,
        @NotBlank @Size(max = 2000)
        @Pattern(regexp = RuleCreateRequest.SOURCE_URL_PATTERN, message = "출처 링크는 http:// 또는 https:// 로 시작해야 합니다.")
        @Schema(description = "링크 (http/https)") String url) {
}
