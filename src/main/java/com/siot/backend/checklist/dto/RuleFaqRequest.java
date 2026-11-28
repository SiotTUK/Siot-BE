package com.siot.backend.checklist.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * 관리자 FAQ 등록·수정 (RUL-04, ADM-06). 수정은 보낸 값으로 통째로 바꾼다 (보내지 않은 언어는 지운다).
 *
 * @param sortOrder    순서 (작을수록 앞)
 * @param ruleCode     자세히 볼 생활수칙 코드. 비우면 연결하지 않는다
 * @param translations 언어(ko, en, zh, vi)별 질문·답변. ko 는 필수. 없는 언어는 회원 화면에서 한국어로 보인다
 */
@Schema(description = "관리자 생활수칙 FAQ 등록·수정 (RUL-04, ADM-06). 수정은 보낸 값으로 통째로 바꾼다")
public record RuleFaqRequest(
        @Schema(description = "순서 (작을수록 앞)", example = "1")
        @NotNull Integer sortOrder,
        @Schema(description = "자세히 볼 생활수칙 코드. 비우면 연결하지 않는다", nullable = true, example = "waste-food")
        @Size(max = 50) String ruleCode,
        @Schema(description = "언어(ko, en, zh, vi)별 질문·답변. ko 는 필수. 보내지 않은 언어는 지운다 (회원 화면은 한국어로 보인다)")
        @NotEmpty Map<String, @NotNull @Valid Text> translations) {

    @Schema(description = "FAQ 질문·답변 (한 언어)")
    public record Text(
            @Schema(description = "질문", example = "음식물 쓰레기는 어디에 버려요?")
            @NotBlank @Size(max = 500) String question,
            @Schema(description = "답변")
            @NotBlank @Size(max = 5000) String answer) {
    }
}
