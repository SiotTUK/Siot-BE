package com.siot.backend.checklist.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "#14 출처 (공식 페이지)")
public record RuleSourceResponse(
        @Schema(description = "페이지 이름 (한국어)", example = "종량제 봉투 배출 안내 (시흥시)") String name,
        @Schema(description = "링크 (http/https)", example = "https://www.siheung.go.kr/portal/contents.do?mId=0401030100") String url) {
}
