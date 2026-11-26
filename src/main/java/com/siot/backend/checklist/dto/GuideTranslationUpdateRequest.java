package com.siot.backend.checklist.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 관리자: 항목 상세 문구 저장 (CHK-06 필드, 한 언어).
 *
 * @param noticeChanged true = 실제 안내 내용 변경 (기한·장소·준비물·수수료 등, NTF-07 변경 표시 대상),
 *                      false = 오타 수정 같은 단순 수정
 */
@Schema(description = "항목 상세 문구 저장 (한 언어, 보낸 값으로 전부 바꿈)")
public record GuideTranslationUpdateRequest(
        @Schema(description = "제목", example = "외국인등록") @NotBlank @Size(max = 255) String title,
        @Schema(description = "요약", nullable = true) @Size(max = 5000) String summary,
        @Schema(description = "본문", nullable = true) @Size(max = 20000) String content,
        @Schema(description = "배지 문구", nullable = true, example = "입국 후 90일 이내") @Size(max = 100) String badge,
        @Schema(description = "신청 장소", nullable = true, example = "관할 출입국·외국인관서") @Size(max = 2000) String place,
        @ArraySchema(arraySchema = @Schema(description = "준비물 (최대 30개, 각 255자)", example = "[\"여권\", \"사진 1장\"]"))
        @Size(max = 30) List<@NotBlank @Size(max = 255) String> docs,
        @Schema(description = "수수료", nullable = true, example = "30,000원") @Size(max = 255) String fee,
        @Schema(description = "근거 법령", nullable = true, example = "출입국관리법 제31조") @Size(max = 255) String law,
        @Schema(description = "안 하면 받는 불이익", nullable = true, example = "과태료") @Size(max = 2000) String penalty,
        @Schema(description = "출처 이름 (링크는 항목 공통 source_url)", nullable = true, example = "하이코리아") @Size(max = 255) String sourceName,
        @Schema(description = "true = 실제 안내 내용 변경 (notice_changed_at 기록, 상세 화면 '변경됨' 표시), false = 오타 수정 같은 단순 수정 (NTF-07)", example = "false")
        @NotNull Boolean noticeChanged,
        @ArraySchema(arraySchema = @Schema(description = "#14 생활수칙 문장 목록 (순서대로). 생략하면 그대로", nullable = true))
        @Size(max = 30) List<@NotBlank @Size(max = 2000) String> points,
        @Schema(description = "#14 생활수칙 시기. 생략하면 그대로, 빈 문자열이면 지움", nullable = true) @Size(max = 2000) String when,
        @Schema(description = "#14 이 언어의 문의처 문구. 생략하면 그대로, 빈 문자열이면 지움", nullable = true) @Size(max = 2000) String contact
) {

    /** 생활수칙 칸(points·when·contact)은 그대로 둔다 */
    public GuideTranslationUpdateRequest(String title, String summary, String content, String badge, String place,
                                         List<String> docs, String fee, String law, String penalty, String sourceName,
                                         Boolean noticeChanged) {
        this(title, summary, content, badge, place, docs, fee, law, penalty, sourceName, noticeChanged, null, null, null);
    }
}
