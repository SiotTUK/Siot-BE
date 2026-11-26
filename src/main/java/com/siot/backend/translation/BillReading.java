package com.siot.backend.translation;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * AI 가 고지서에서 읽은 값 (RUL-07). 고지서에 없거나 읽지 못한 값은 null.
 * 지방세 법정 서식: 별지 제8호 납세고지서, 제29호 독촉장, 제31호 체납액 고지서.
 *
 * @param readable      고지서로 읽을 수 있는지. false 면 retakeReason 외에는 비어 있다
 * @param retakeReason  readable=false 일 때 {@link #RETAKE_REASONS}
 * @param documentType  {@link #DOCUMENT_TYPES}
 * @param issuer        고지 기관 (원문)
 * @param taxType       세목 (원문: 자동차세, 재산세 …)
 * @param taxObject     과세대상. 차량번호·주소는 앞부분만
 * @param amount        납기 내 금액 (원)
 * @param dueDate       납기 내 기한
 * @param lateAmount    납기 후 금액 (원)
 * @param lateDueDate   납기 후 기한
 * @param penalty       가산금·가산세 (원)
 * @param paymentMethods 고지서에 적힌 납부 방법 (사용자 언어, 계좌번호·전자납부번호 없음)
 * @param contact       문의 전화
 * @param originalLines 근거가 된 고지서 원문 줄 (한국어 그대로)
 * @param summary       사용자 언어 요약 4줄
 */
public record BillReading(
        boolean readable,
        String retakeReason,
        String documentType,
        String issuer,
        String taxType,
        String taxObject,
        Long amount,
        LocalDate dueDate,
        Long lateAmount,
        LocalDate lateDueDate,
        Long penalty,
        List<String> paymentMethods,
        String contact,
        List<String> originalLines,
        Summary summary
) {

    public static final String TAX_NOTICE = "TAX_NOTICE";
    public static final String DEMAND_NOTICE = "DEMAND_NOTICE";
    public static final String ARREARS_NOTICE = "ARREARS_NOTICE";
    public static final String UTILITY = "UTILITY";
    public static final String OTHER = "OTHER";
    public static final List<String> DOCUMENT_TYPES = List.of(TAX_NOTICE, DEMAND_NOTICE, ARREARS_NOTICE, UTILITY, OTHER);
    /** 지방세 서식 (가산세·체납 경고 대상) */
    public static final Set<String> LOCAL_TAX_TYPES = Set.of(TAX_NOTICE, DEMAND_NOTICE, ARREARS_NOTICE);

    public static final String BLURRY = "BLURRY";
    public static final String CROPPED = "CROPPED";
    public static final String NOT_A_BILL = "NOT_A_BILL";
    public static final List<String> RETAKE_REASONS = List.of(BLURRY, CROPPED, NOT_A_BILL);

    public BillReading {
        paymentMethods = paymentMethods == null ? List.of() : List.copyOf(paymentMethods);
        originalLines = originalLines == null ? List.of() : List.copyOf(originalLines);
    }

    /** 무엇인지 / 언제까지 / 얼마인지 / 어떻게 내는지 */
    public record Summary(String what, String due, String amount, String howToPay) {
    }

    public static BillReading unreadable(String retakeReason) {
        return new BillReading(false, retakeReason, null, null, null, null, null, null, null, null, null,
                List.of(), null, List.of(), null);
    }
}
