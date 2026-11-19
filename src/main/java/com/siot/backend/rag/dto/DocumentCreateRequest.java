package com.siot.backend.rag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

/** ADM-02·ADM-03: 새 문서 등록 (multipart/form-data) */
@Schema(description = "ADM-02 새 문서 등록")
public record DocumentCreateRequest(
        @Schema(description = "PDF 파일 (30MB 이하)") @NotNull MultipartFile file,
        @Schema(description = "문서명 (챗봇 출처에 그대로 표시)", example = "시흥시 외국인주민 생활안내")
        @NotBlank @Size(max = 255) String name,
        @Schema(description = "공개 원문 링크 (http/https)", example = "https://www.siheung.go.kr/guide.pdf")
        @NotBlank @Size(max = 2000) @Pattern(regexp = DocumentUpdateRequest.URL_PATTERN) String sourceUrl,
        @Schema(description = "발행일", nullable = true, example = "2026-03-01")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate publishedOn) {
}
