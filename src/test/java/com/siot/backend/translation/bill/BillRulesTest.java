package com.siot.backend.translation.bill;

import com.siot.backend.translation.BillReading;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** RUL-07 서버 규칙: 경고, 개인정보 가림, 관련 안내 매핑, 요약 확인 */
class BillRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 21);

    // ---- 경고 ----

    @Test
    void taxNoticeGetsLatePenaltyOnly() {
        assertThat(codes(bill("TAX_NOTICE", LocalDate.of(2026, 12, 31)))).containsExactly("LATE_PENALTY");
    }

    @Test
    void demandNoticeWarnsSeizureAndStayExtension() {
        List<BillWarnings.Warning> warnings = BillWarnings.of(bill("DEMAND_NOTICE", LocalDate.of(2026, 12, 10)), "en", TODAY);

        assertThat(warnings).extracting(BillWarnings.Warning::code)
                .containsExactly("LATE_PENALTY", "SEIZURE", "STAY_EXTENSION");
        assertThat(warnings.get(1).message()).contains("seized");
        assertThat(warnings.get(2).message()).contains("period of stay");
    }

    @Test
    void arrearsNoticeWarnsStayExtensionButNotSeizure() {
        assertThat(codes(bill("ARREARS_NOTICE", null))).containsExactly("LATE_PENALTY", "STAY_EXTENSION");
    }

    @Test
    void utilityBillHasNoLocalTaxPenalty() {
        assertThat(codes(bill("UTILITY", LocalDate.of(2026, 12, 31)))).isEmpty();
    }

    @Test
    void pastDueDateAddsOverdueWithDate() {
        List<BillWarnings.Warning> warnings = BillWarnings.of(bill("TAX_NOTICE", LocalDate.of(2026, 11, 20)), "vi", TODAY);

        assertThat(warnings).extracting(BillWarnings.Warning::code).containsExactly("LATE_PENALTY", "OVERDUE");
        assertThat(warnings.get(1).message()).contains("2026-11-20").startsWith("Đã quá hạn");
        // 오늘이 기한이면 아직 지나지 않았다
        assertThat(codes(bill("TAX_NOTICE", TODAY))).containsExactly("LATE_PENALTY");
    }

    @Test
    void everyWarningAndRetakeHasFourLanguages() {
        for (String language : List.of("ko", "en", "zh", "vi")) {
            for (String code : BillMessages.warningCodes()) {
                assertThat(BillMessages.warning(code, language, TODAY)).isNotBlank().doesNotContain("%s");
            }
            for (String reason : BillReading.RETAKE_REASONS) {
                assertThat(BillMessages.retake(reason, language)).isNotBlank();
            }
        }
        assertThat(BillMessages.warning("LATE_PENALTY", "ko", null)).contains("3%", "45만 원", "0.66%", "60개월");
        // 공과금·보험료에도 붙는 문구라 연체금도 함께 적는다
        assertThat(BillMessages.warning("OVERDUE", "ko", TODAY)).contains("가산세", "연체금");
    }

    // ---- 개인정보 가림 ----

    @Test
    void masksRegistrationNumbersAndLongNumberRuns() {
        assertThat(BillMasker.mask("주민번호 900101-5123456")).isEqualTo("주민번호 ******-*******");
        assertThat(BillMasker.mask("전자납부번호 4139-0123-4567-8901-2345")).isEqualTo("전자납부번호 ****-****-****-****-****");
        assertThat(BillMasker.mask("가상계좌 농협 79012345678901")).isEqualTo("가상계좌 농협 **************");
    }

    @Test
    void masksVehicleAndAddressNumbers() {
        assertThat(BillMasker.mask("과세대상 12가3456")).isEqualTo("과세대상 12가****");
        assertThat(BillMasker.mask("123나 4567 승용")).isEqualTo("123나**** 승용");
        assertThat(BillMasker.mask("경기도 시흥시 정왕대로 233")).isEqualTo("경기도 시흥시 정왕대로 ***");
        assertThat(BillMasker.mask("시흥시 정왕동 1234-5")).isEqualTo("시흥시 정왕동 ***");
        assertThat(BillMasker.mask("○○아파트 101동 1203호")).isEqualTo("○○아파트 ***동 ***호");
    }

    @Test
    void keepsAmountsDatesAndInstitutionPhones() {
        String line = "납기내 2026-12-31 까지 123,450원 (가산금 3%) 문의 031-310-2114";
        assertThat(BillMasker.mask(line)).isEqualTo(line);
    }

    // ---- 관련 안내 ----

    @Test
    void mapsTaxTypeToGuideCodes() {
        assertThat(BillGuideLinks.codes("자동차세")).containsExactly("CL-10");
        assertThat(BillGuideLinks.codes("재산세(건축물)")).containsExactly("CL-11");
        assertThat(BillGuideLinks.codes("재산세 도시지역분")).containsExactly("CL-11");
        assertThat(BillGuideLinks.codes("주민세")).isEmpty();
        assertThat(BillGuideLinks.codes(null)).isEmpty();
    }

    // ---- 요약 확인 ----

    @Test
    void acceptsSummaryWhoseAmountAndDateMatch() {
        BillReading reading = withSummary(123450L, LocalDate.of(2026, 12, 31), new BillReading.Summary(
                "2026 car tax notice from Siheung City", "Pay by 2026-12-31.", "Pay 123,450 won.",
                "Pay at a bank or on Wetax. Call 031-310-2114."));

        assertThat(BillSummaryCheck.consistent(reading)).isTrue();
    }

    @Test
    void acceptsVietnameseThousandsSeparatorAndKoreanDate() {
        BillReading reading = withSummary(123450L, LocalDate.of(2026, 12, 31), new BillReading.Summary(
                "Thuế ô tô", "Hạn nộp: 2026년 12월 31일", "123.450 won", "Ngân hàng"));

        assertThat(BillSummaryCheck.consistent(reading)).isTrue();
    }

    @Test
    void rejectsSummaryWithDifferentAmount() {
        BillReading reading = withSummary(123450L, LocalDate.of(2026, 12, 31), new BillReading.Summary(
                "Car tax", "Pay by 2026-12-31.", "Pay 132,450 won.", "Bank"));

        assertThat(BillSummaryCheck.consistent(reading)).isFalse();
    }

    @Test
    void rejectsSummaryWithDifferentOrMissingDate() {
        assertThat(BillSummaryCheck.consistent(withSummary(123450L, LocalDate.of(2026, 12, 31),
                new BillReading.Summary("Car tax", "Pay by 2026-12-30.", "123,450 won", "Bank")))).isFalse();
        assertThat(BillSummaryCheck.consistent(withSummary(123450L, LocalDate.of(2026, 12, 31),
                new BillReading.Summary("Car tax", "Pay by the end of December.", "123,450 won", "Bank")))).isFalse();
    }

    @Test
    void rejectsAmountInSummaryWhenAmountWasNotRead() {
        assertThat(BillSummaryCheck.consistent(withSummary(null, LocalDate.of(2026, 12, 31),
                new BillReading.Summary("Car tax", "2026-12-31", "About 120,000 won", "Bank")))).isFalse();
        assertThat(BillSummaryCheck.consistent(withSummary(null, null,
                new BillReading.Summary("Car tax", "Could not be read", "Could not be read", "Bank")))).isTrue();
    }

    @Test
    void serverSummaryUsesExtractedValues() {
        BillReading reading = withSummary(123450L, LocalDate.of(2026, 12, 31), null);

        BillReading.Summary summary = BillMessages.summary(reading, "en");

        assertThat(summary.what()).isEqualTo("Tax notice - 자동차세 (시흥시)");
        assertThat(summary.due()).isEqualTo("Pay by: 2026-12-31");
        assertThat(summary.amount()).isEqualTo("Amount to pay: 123,450 won");
        assertThat(summary.howToPay()).isEqualTo("How to pay: Wetax (위택스), Bank (은행)");
        assertThat(BillSummaryCheck.consistent(new BillReading(true, null, "TAX_NOTICE", "시흥시", "자동차세", null,
                123450L, LocalDate.of(2026, 12, 31), null, null, null, List.of(), null, List.of(), summary))).isTrue();
    }

    private static List<String> codes(BillReading reading) {
        return BillWarnings.of(reading, "ko", TODAY).stream().map(BillWarnings.Warning::code).toList();
    }

    private static BillReading bill(String documentType, LocalDate dueDate) {
        return new BillReading(true, null, documentType, "시흥시", "자동차세", null, 10000L, dueDate, null, null, null,
                List.of(), null, List.of(), null);
    }

    private static BillReading withSummary(Long amount, LocalDate dueDate, BillReading.Summary summary) {
        return new BillReading(true, null, "TAX_NOTICE", "시흥시", "자동차세", "12가****", amount, dueDate, null, null,
                null, List.of("Wetax (위택스)", "Bank (은행)"), "031-310-2114", List.of(), summary);
    }
}
