package com.siot.backend.checklist.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * @param noticeChangedAt NTF-07 실제 안내 내용이 바뀐 시각 (null = 등록 후 바뀐 적 없음)
 * @param updatedAt       CHK-06 마지막 수정 시각 (단순 수정 포함)
 */
@Schema(description = "항목 상세 문구 (한 언어)")
public record GuideTranslationResponse(
        @Schema(description = "항목 ID", example = "CL-01") String code,
        @Schema(description = "문구 언어", example = "ko") String language,
        @Schema(description = "제목", example = "외국인등록") String title,
        @Schema(description = "요약", nullable = true) String summary,
        @Schema(description = "본문", nullable = true) String content,
        @Schema(description = "배지 문구", nullable = true, example = "입국 후 90일 이내") String badge,
        @Schema(description = "신청 장소", nullable = true) String place,
        @Schema(description = "준비물 (없으면 빈 목록)") List<String> docs,
        @Schema(description = "수수료", nullable = true) String fee,
        @Schema(description = "근거 법령", nullable = true) String law,
        @Schema(description = "안 하면 받는 불이익", nullable = true) String penalty,
        @Schema(description = "출처 이름", nullable = true) String sourceName,
        @Schema(description = "실제 안내 내용이 바뀐 시각 (NTF-07). null = 등록 후 바뀐 적 없음", nullable = true) OffsetDateTime noticeChangedAt,
        @Schema(description = "마지막 수정 시각 (CHK-06, 단순 수정 포함)") OffsetDateTime updatedAt,
        // CHK-06 출처 쪽수
        @Schema(description = "CHK-06 출처 안내책자 시작 쪽수", example = "12", nullable = true) Integer pageStart,
        @Schema(description = "CHK-06 출처 안내책자 종료 쪽수", example = "14", nullable = true) Integer pageEnd,
        @Schema(description = "CHK-06 출처 쪽수 표기", example = "p.12~14", nullable = true) String sourcePages,
        // #14 생활수칙 칸 (저장 요청 GuideTranslationUpdateRequest 와 같은 이름). 체크리스트 항목은 빈 목록·null (BE #23)
        @Schema(description = "#14 생활수칙 문장 목록 (순서대로). 체크리스트 항목은 빈 목록") List<String> points,
        @Schema(description = "#14 생활수칙 시기", nullable = true) String when,
        @Schema(description = "#14 이 언어의 문의처 문구. null 이면 화면은 항목 공통 문의 전화(PATCH /api/admin/guides/{code})를 쓴다",
                nullable = true) String contact,
        @Schema(description = "#14 사람이 확인한 번역이면 true (자동 번역이 덮어쓰지 않는다). ko 외 언어를 관리자가 저장하면 true, "
                + "자동 생성 번역은 false", example = "false") boolean reviewed
) {
}
