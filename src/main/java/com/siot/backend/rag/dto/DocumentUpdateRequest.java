package com.siot.backend.rag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** ADM-03: 문서 정보 수정. 보내지 않은(null) 값은 바꾸지 않는다 */
@Schema(description = "ADM-03 문서 정보 수정 (보낸 값만 바뀜)")
public record DocumentUpdateRequest(
        @Schema(description = "문서명", nullable = true, example = "시흥시 외국인주민 생활안내 (2026)")
        @Size(min = 1, max = 255) String name,
        @Schema(description = "공개 원문 링크 (http/https)", nullable = true, example = "https://www.siheung.go.kr/guide-2026.pdf")
        @Size(max = 2000) @Pattern(regexp = URL_PATTERN) String sourceUrl,
        @Schema(description = "발행일", nullable = true, example = "2026-03-01") LocalDate publishedOn,
        @Schema(description = "사용 여부. false 면 챗봇 검색에서 빠진다 (버전·조각은 남음)", nullable = true, example = "false")
        Boolean active) {

    public static final String URL_PATTERN = "^https?://\\S+$";
}
