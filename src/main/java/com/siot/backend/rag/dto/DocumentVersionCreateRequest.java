package com.siot.backend.rag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

/** ADM-02: 기존 문서의 새 버전 (multipart/form-data) */
@Schema(description = "ADM-02 새 버전 업로드")
public record DocumentVersionCreateRequest(
        @Schema(description = "PDF 파일 (30MB 이하)") @NotNull MultipartFile file,
        @Schema(description = "바뀐 내용 요약 (1000자 이하)", nullable = true, example = "2026년 건강보험료 기준 반영")
        @Size(max = 1000) String changeSummary) {
}
