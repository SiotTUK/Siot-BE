package com.siot.backend.report.dto;

import com.siot.backend.global.common.SupportedLanguages;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * COM-01 콘텐츠 오류 신고. 대상은 행사(eventId) 또는 안내 항목(guideId: 생활수칙·체크리스트·생활 변화 할 일) 중 하나.
 * 문제가 있는 문장을 인용하고 사유(번역 오류 / 내용 오류 / 기타)를 고른다. 화면 언어를 함께 저장한다.
 */
@Schema(description = "콘텐츠 오류 신고 접수 요청 (COM-01). eventId 와 guideId 중 하나만 보낸다")
public record ContentReportRequest(
        @Schema(description = "안내 항목 ID: 생활수칙·체크리스트·생활 변화 할 일 (체크리스트 응답의 guideId). 행사 신고면 null", nullable = true, example = "1")
        Long guideId,

        @Schema(description = "행사 ID (생활수칙 신고면 null)", nullable = true, example = "null")
        Long eventId,

        @Size(max = 255)
        @Schema(description = "신고 대상 제목. 비우면 서버가 대상의 한국어 제목으로 채운다", nullable = true, example = "음식물 쓰레기 배출")
        String targetTitleSnapshot,

        @NotBlank
        @Size(max = 2000)
        @Schema(description = "문제가 있는 문장 (화면에서 인용한 그대로)", example = "수수료 30,000원")
        String quotedText,

        @Pattern(regexp = SupportedLanguages.PATTERN)
        @Schema(description = "신고 당시 화면 언어 (ko/en/zh/vi, 비우면 ko)", nullable = true, example = "en")
        String language,

        @NotBlank
        @Pattern(regexp = REASON_PATTERN)
        @Schema(description = "사유: TRANSLATION(번역 오류) / CONTENT(내용 오류) / OTHER(기타)",
                allowableValues = {"TRANSLATION", "CONTENT", "OTHER"}, example = "CONTENT")
        String reason,

        @Size(max = 2000)
        @Schema(description = "상세 설명 (선택)", nullable = true, example = "수수료가 인상되어 35,000원으로 변경되었습니다.")
        String description,

        @Size(max = 50)
        @Schema(description = "#14 안내 항목 코드 (생활수칙 슬러그 waste-food, 체크리스트 CL-01, 생활 변화 할 일 LC-01-1). "
                + "guideId 대신 보낼 수 있다. eventId·guideId·guideCode 중 대상은 하나", nullable = true, example = "waste-food")
        String guideCode
) {
    public static final String REASON_PATTERN = "TRANSLATION|CONTENT|OTHER";

    /** guideCode 없이 */
    public ContentReportRequest(Long guideId, Long eventId, String targetTitleSnapshot, String quotedText,
                                String language, String reason, String description) {
        this(guideId, eventId, targetTitleSnapshot, quotedText, language, reason, description, null);
    }
}
