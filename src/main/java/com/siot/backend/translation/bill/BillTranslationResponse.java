package com.siot.backend.translation.bill;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

/** RUL-07 고지서 사진 번역 결과. 저장하지 않는다 */
@Schema(description = "고지서 사진 번역 결과. readable=false 면 retake 만 채워진다")
public record BillTranslationResponse(
        @Schema(description = "요약·경고 언어 (ko/en/zh/vi)", example = "en") String language,
        @Schema(description = "고지서로 읽을 수 있었는지. false 면 다시 찍기 안내(retake)를 보여 준다") boolean readable,
        @Schema(description = "다시 찍기 안내 (readable=false 일 때만)", nullable = true) Retake retake,
        @Schema(description = "TAX_NOTICE(납세고지서) / DEMAND_NOTICE(독촉장) / ARREARS_NOTICE(체납액 고지서) / "
                + "UTILITY(공과금·보험료) / OTHER", nullable = true, example = "TAX_NOTICE") String documentType,
        @Schema(description = "고지 기관 (원문)", nullable = true, example = "시흥시") String issuer,
        @Schema(description = "세목 (원문)", nullable = true, example = "자동차세") String taxType,
        @Schema(description = "과세대상 (원문, 차량번호·주소 가림)", nullable = true, example = "12가****") String taxObject,
        @Schema(description = "납기 내 금액 (원)", nullable = true, example = "123450") Long amount,
        @Schema(description = "납기 내 기한", nullable = true, example = "2026-12-31") LocalDate dueDate,
        @Schema(description = "납기 후 금액 (원)", nullable = true) Long lateAmount,
        @Schema(description = "납기 후 기한", nullable = true) LocalDate lateDueDate,
        @Schema(description = "가산금·가산세 (원)", nullable = true) Long penalty,
        @Schema(description = "고지서에 적힌 납부 방법 (사용자 언어). 계좌·납부번호 없음") List<String> paymentMethods,
        @Schema(description = "문의 전화", nullable = true, example = "031-310-2114") String contact,
        @Schema(description = "근거가 된 고지서 원문 줄 (한국어, 개인정보 가림)") List<String> originalLines,
        @Schema(description = "사용자 언어 요약 4줄 (readable=true 일 때)", nullable = true) Summary summary,
        @Schema(description = "AI: AI 가 쓴 요약, SERVER: AI 요약의 금액·날짜가 추출 값과 달라 서버가 만든 요약",
                nullable = true, example = "AI") String summarySource,
        @Schema(description = "주의 사항 (사용자 언어)") List<Warning> warnings,
        @Schema(description = "관련 안내 항목. 어느 화면으로 연결할지는 프론트가 정한다") List<RelatedGuide> relatedGuides,
        @Schema(description = "PDF 가 여러 쪽이라 첫 쪽만 읽었는지") boolean firstPageOnly,
        @Schema(description = "PDF 쪽 수 (사진은 1)", example = "1") int pageCount
) {

    @Schema(description = "다시 찍기 안내")
    public record Retake(
            @Schema(description = "BLURRY(흐림·어두움) / CROPPED(잘림) / NOT_A_BILL(고지서 아님)", example = "BLURRY") String reason,
            @Schema(description = "사용자 언어 안내") String message) {
    }

    @Schema(description = "무엇인지 / 언제까지 / 얼마인지 / 어떻게 내는지")
    public record Summary(String what, String due, String amount, String howToPay) {
    }

    @Schema(description = "주의 사항")
    public record Warning(
            @Schema(description = "LATE_PENALTY(가산세) / SEIZURE(압류) / STAY_EXTENSION(체류기간 연장 제한) / OVERDUE(기한 지남)",
                    example = "LATE_PENALTY") String code,
            @Schema(description = "사용자 언어 문구") String message) {
    }

    @Schema(description = "관련 안내 항목 (guide)")
    public record RelatedGuide(
            @Schema(description = "항목 코드", example = "CL-10") String code,
            @Schema(description = "항목 종류 (CHECKLIST, RULE, LIFE_CHANGE_TASK)", example = "CHECKLIST") String kind,
            @Schema(description = "제목 (사용자 언어, 없으면 한국어)", example = "자동차세 내기") String title) {
    }
}
