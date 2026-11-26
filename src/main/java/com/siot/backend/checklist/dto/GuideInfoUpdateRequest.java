package com.siot.backend.checklist.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "관리자: 항목의 출처 링크·문의 전화 수정 (NTF-07, CHK-06). 보내지 않은 값은 그대로, 빈 문자열은 지운다")
public record GuideInfoUpdateRequest(
        @Size(max = 2000)
        @Pattern(regexp = "^$|^\\s*https?://\\S+\\s*$", message = "출처 링크는 http:// 또는 https:// 로 시작해야 합니다.")
        @Schema(description = "출처 링크 (원문 링크)", nullable = true, example = "https://www.hikorea.go.kr")
        String sourceUrl,
        @Size(max = 255)
        @Schema(description = "문의 전화", nullable = true, example = "1345")
        String contact
) {
}
