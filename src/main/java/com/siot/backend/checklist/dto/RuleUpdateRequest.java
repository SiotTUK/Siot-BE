package com.siot.backend.checklist.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 입력값 규칙은 등록(RUL-09)과 같다: 제목·본문 필수. 카테고리·정렬·FAQ 는 보내지 않으면 그대로 (RUL-10) */
@Schema(description = "관리자 생활수칙 수정 요청 (RUL-10, ADM-06)")
public record RuleUpdateRequest(
        @Size(max = 100)
        @Pattern(regexp = "(?s).*\\S.*", message = "카테고리를 비워 둘 수 없습니다. 바꾸지 않으려면 보내지 마세요.")
        @Schema(description = "카테고리. 보내지 않으면 그대로", example = "쓰레기 배출")
        String category,

        @Schema(description = "정렬 순서. 보내지 않으면 그대로", example = "10")
        Integer sortOrder,

        @Schema(description = "FAQ TOP 5 표시 여부. 보내지 않으면 그대로", example = "true")
        Boolean faq,

        @Size(max = 255)
        @Schema(description = "문의 전화")
        String contact,

        @Size(max = 2000)
        @Pattern(regexp = RuleCreateRequest.SOURCE_URL_PATTERN, message = "출처 링크는 http:// 또는 https:// 로 시작해야 합니다.")
        @Schema(description = "출처 링크 (http/https)")
        String sourceUrl,

        @NotBlank
        @Size(max = 255)
        @Schema(description = "제목 (한국어)", example = "대형 폐기물 스티커 배출 방법")
        String title,

        @Size(max = 5000)
        @Schema(description = "요약")
        String summary,

        @NotBlank
        @Size(max = 20000)
        @Schema(description = "본문 (한국어)")
        String content,

        @Size(max = 100)
        @Schema(description = "배지 문구")
        String badge,

        @Size(max = 2000)
        @Schema(description = "배출/신청 장소")
        String place,

        @Size(max = 30)
        @Schema(description = "준비물 목록 (최대 30개, 각 255자)")
        List<@NotBlank @Size(max = 255) String> docs,

        @Size(max = 255)
        @Schema(description = "수수료")
        String fee,

        @Size(max = 255)
        @Schema(description = "근거 법령")
        String law,

        @Size(max = 2000)
        @Schema(description = "과태료 및 불이익")
        String penalty,

        @Size(max = 255)
        @Schema(description = "출처 이름")
        String sourceName,

        @NotNull
        @Schema(description = "실제 안내 내용 변경 여부 (NTF-07: 변경 알림 발송 유도)", example = "true")
        Boolean noticeChanged
) {
}
