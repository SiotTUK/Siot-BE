package com.siot.backend.translation.bill;

import com.siot.backend.global.common.SupportedLanguages;
import com.siot.backend.translation.BillReading;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * RUL-07 서버가 붙이는 고정 문구 (4개 언어). AI 로 만들지 않는다: 경고, 다시 찍기 안내, AI 요약을 버렸을 때의 요약.
 */
final class BillMessages {

    // ---- 경고 코드 ----
    static final String LATE_PENALTY = "LATE_PENALTY";
    static final String SEIZURE = "SEIZURE";
    static final String STAY_EXTENSION = "STAY_EXTENSION";
    static final String OVERDUE = "OVERDUE";

    private static final Map<String, Map<String, String>> WARNINGS = Map.of(
            LATE_PENALTY, Map.of(
                    "ko", "납부 기한이 지나면 3%의 가산세가 붙고, 세금이 45만 원 이상이면 한 달마다 0.66%가 더 붙습니다(최대 60개월).",
                    "en", "If you pay after the due date, a 3% late-payment penalty is added. If the tax is 450,000 won or more, "
                            + "another 0.66% is added every month (up to 60 months).",
                    "zh", "超过缴纳期限将加收3%的滞纳加算税；税额在45万韩元以上的，每月再加收0.66%（最多60个月）。",
                    "vi", "Nếu nộp sau hạn, bạn sẽ bị cộng thêm 3% tiền phạt chậm nộp. Nếu số thuế từ 450.000 won trở lên, "
                            + "mỗi tháng sẽ cộng thêm 0,66% (tối đa 60 tháng)."),
            SEIZURE, Map.of(
                    "ko", "독촉장에 적힌 기한까지 내지 않으면 재산이 압류될 수 있습니다.",
                    "en", "If you do not pay by the deadline on this demand notice, your property may be seized.",
                    "zh", "如未在催缴通知书规定的期限内缴纳，您的财产可能会被扣押。",
                    "vi", "Nếu không nộp trước hạn ghi trên giấy đôn đốc này, tài sản của bạn có thể bị tịch biên."),
            STAY_EXTENSION, Map.of(
                    "ko", "지방세를 체납하면 체류기간 연장이 제한될 수 있습니다.",
                    "en", "If you have unpaid local taxes, the extension of your period of stay may be restricted.",
                    "zh", "如拖欠地方税，延长滞留期间可能会受到限制。",
                    "vi", "Nếu nợ thuế địa phương, việc gia hạn thời gian lưu trú của bạn có thể bị hạn chế."),
            OVERDUE, Map.of(
                    "ko", "납부 기한(%s)이 지났습니다. 가산세나 연체금이 붙었을 수 있으니 고지서의 문의처에 확인해 주세요.",
                    "en", "The due date (%s) has passed. A penalty may have been added, so please check with the contact on the bill.",
                    "zh", "缴纳期限（%s）已过。可能已加收滞纳金，请向通知书上的咨询处确认。",
                    "vi", "Đã quá hạn nộp (%s). Có thể đã bị cộng tiền phạt, hãy hỏi nơi liên hệ ghi trên giấy báo."));

    private static final Map<String, Map<String, String>> RETAKE = Map.of(
            BillReading.BLURRY, Map.of(
                    "ko", "글자가 흐리거나 어두워서 읽지 못했어요. 밝은 곳에서 흔들리지 않게 다시 찍어 주세요.",
                    "en", "The text is blurry or too dark to read. Please take the photo again in a bright place and hold the camera steady.",
                    "zh", "文字模糊或太暗，无法识别。请在明亮的地方保持稳定后重新拍摄。",
                    "vi", "Chữ bị mờ hoặc quá tối nên không đọc được. Hãy chụp lại ở nơi sáng và giữ máy thật chắc."),
            BillReading.CROPPED, Map.of(
                    "ko", "고지서 일부가 잘려서 읽지 못했어요. 고지서 전체가 화면에 들어오게 다시 찍어 주세요.",
                    "en", "Part of the bill is cut off. Please take the photo again so the whole bill fits on the screen.",
                    "zh", "通知书有部分被截掉，无法识别。请让整张通知书都在画面内后重新拍摄。",
                    "vi", "Một phần giấy báo bị cắt mất. Hãy chụp lại sao cho toàn bộ giấy báo nằm trong khung hình."),
            BillReading.NOT_A_BILL, Map.of(
                    "ko", "고지서로 보이지 않아요. 세금이나 공과금 고지서를 찍어 주세요.",
                    "en", "This does not look like a bill. Please take a photo of a tax or utility bill.",
                    "zh", "这看起来不是缴费通知书。请拍摄税金或公共费用的缴费通知书。",
                    "vi", "Đây có vẻ không phải giấy báo nộp tiền. Hãy chụp giấy báo thuế hoặc hóa đơn tiện ích."));

    private static final Map<String, Map<String, String>> DOCUMENT_LABELS = Map.of(
            BillReading.TAX_NOTICE, Map.of("ko", "납세고지서", "en", "Tax notice", "zh", "纳税通知书", "vi", "Giấy báo nộp thuế"),
            BillReading.DEMAND_NOTICE, Map.of("ko", "독촉장", "en", "Demand notice for unpaid tax", "zh", "催缴通知书",
                    "vi", "Giấy đôn đốc nộp thuế"),
            BillReading.ARREARS_NOTICE, Map.of("ko", "체납액 고지서", "en", "Notice of unpaid tax", "zh", "欠税通知书",
                    "vi", "Giấy báo số thuế còn nợ"),
            BillReading.UTILITY, Map.of("ko", "공과금·보험료 고지서", "en", "Utility or insurance bill", "zh", "公共费用·保险费通知书",
                    "vi", "Hóa đơn tiện ích hoặc bảo hiểm"),
            BillReading.OTHER, Map.of("ko", "고지서", "en", "Bill", "zh", "缴费通知书", "vi", "Giấy báo nộp tiền"));

    private static final Map<String, String> DUE = Map.of(
            "ko", "납부 기한: %s", "en", "Pay by: %s", "zh", "缴纳期限：%s", "vi", "Hạn nộp: %s");
    private static final Map<String, String> DUE_UNKNOWN = Map.of(
            "ko", "납부 기한을 읽지 못했습니다. 고지서를 확인해 주세요.",
            "en", "The due date could not be read. Please check the bill.",
            "zh", "未能识别缴纳期限，请查看通知书。",
            "vi", "Không đọc được hạn nộp. Hãy kiểm tra giấy báo.");
    private static final Map<String, String> AMOUNT = Map.of(
            "ko", "납부 금액: %s원", "en", "Amount to pay: %s won", "zh", "缴纳金额：%s韩元", "vi", "Số tiền phải nộp: %s won");
    private static final Map<String, String> AMOUNT_UNKNOWN = Map.of(
            "ko", "납부 금액을 읽지 못했습니다. 고지서를 확인해 주세요.",
            "en", "The amount could not be read. Please check the bill.",
            "zh", "未能识别缴纳金额，请查看通知书。",
            "vi", "Không đọc được số tiền. Hãy kiểm tra giấy báo.");
    private static final Map<String, String> HOW_TO_PAY = Map.of(
            "ko", "납부 방법: %s", "en", "How to pay: %s", "zh", "缴纳方式：%s", "vi", "Cách nộp: %s");
    private static final Map<String, String> HOW_TO_PAY_UNKNOWN = Map.of(
            "ko", "납부 방법은 고지서 뒷면이나 문의처에서 확인해 주세요.",
            "en", "Check the back of the bill or ask the contact on it for how to pay.",
            "zh", "请查看通知书背面或向咨询处了解缴纳方式。",
            "vi", "Hãy xem mặt sau giấy báo hoặc hỏi nơi liên hệ để biết cách nộp.");

    private BillMessages() {
    }

    static String warning(String code, String language, LocalDate dueDate) {
        String text = pick(WARNINGS.get(code), language);
        return OVERDUE.equals(code) ? text.formatted(dueDate) : text;
    }

    static String retake(String reason, String language) {
        return pick(RETAKE.getOrDefault(reason, RETAKE.get(BillReading.BLURRY)), language);
    }

    /** AI 요약의 금액·날짜가 추출 값과 다를 때 쓰는 서버 요약 */
    static BillReading.Summary summary(BillReading reading, String language) {
        String label = pick(DOCUMENT_LABELS.getOrDefault(reading.documentType(), DOCUMENT_LABELS.get(BillReading.OTHER)),
                language);
        StringBuilder what = new StringBuilder(label);
        if (reading.taxType() != null) {
            what.append(" - ").append(reading.taxType());
        }
        if (reading.issuer() != null) {
            what.append(" (").append(reading.issuer()).append(')');
        }
        String due = reading.dueDate() == null
                ? pick(DUE_UNKNOWN, language)
                : pick(DUE, language).formatted(reading.dueDate());
        String amount = reading.amount() == null
                ? pick(AMOUNT_UNKNOWN, language)
                : pick(AMOUNT, language).formatted(won(reading.amount()));
        String howToPay = reading.paymentMethods().isEmpty()
                ? pick(HOW_TO_PAY_UNKNOWN, language)
                : pick(HOW_TO_PAY, language).formatted(String.join("zh".equals(language) ? "、" : ", ",
                reading.paymentMethods()));
        return new BillReading.Summary(what.toString(), due, amount, howToPay);
    }

    static String won(long amount) {
        return String.format(Locale.ROOT, "%,d", amount);
    }

    private static String pick(Map<String, String> texts, String language) {
        return texts.getOrDefault(language, texts.get(SupportedLanguages.DEFAULT));
    }

    /** 서식 이름 (사용자 언어). 모르는 서식이면 "고지서" */
    static String documentLabel(String documentType, String language) {
        Map<String, String> labels = DOCUMENT_LABELS.get(documentType);
        return pick(labels == null ? DOCUMENT_LABELS.get(BillReading.OTHER) : labels, language);
    }

    /** 납부 마감 알림 제목 (name, leadDays) */
    private static final Map<String, String> DUE_REMINDER_TITLE = Map.of(
            "ko", "[%s] 납부 마감 D-%d 안내",
            "en", "[%s] Payment due in %d day(s)",
            "zh", "[%s] 缴纳截止 D-%d 提醒",
            "vi", "[%s] Còn %d ngày đến hạn nộp");

    /** 납부 마감 알림 본문 (name, dueDate, leadDays, amountText) */
    private static final Map<String, String> DUE_REMINDER_BODY = Map.of(
            "ko", "번역해 둔 %s의 납부 마감일(%s)까지 %d일 남았습니다.%s 고지서에 적힌 방법으로 기한 안에 내 주세요.",
            "en", "The payment deadline for the %s you translated is %s, %d day(s) from now.%s Please pay on time using a method on the bill.",
            "zh", "您翻译的%s缴纳截止日为%s，还剩%d天。%s请按通知书上的方式按期缴纳。",
            "vi", "Hạn nộp %s bạn đã dịch là %s, còn %d ngày.%s Hãy nộp đúng hạn theo cách ghi trên giấy báo.");

    /** 금액 문구 (won) — 본문 가운데에 들어간다 */
    private static final Map<String, String> DUE_REMINDER_AMOUNT = Map.of(
            "ko", " 납부 금액은 %s원입니다.",
            "en", " The amount is %s won.",
            "zh", "缴纳金额为%s韩元。",
            "vi", " Số tiền là %s won.");

    static String dueReminderTitle(String name, int leadDays, String language) {
        return pick(DUE_REMINDER_TITLE, language).formatted(name, leadDays);
    }

    static String dueReminderBody(String name, LocalDate dueDate, int leadDays, Long amount, String language) {
        String amountText = amount == null ? "" : pick(DUE_REMINDER_AMOUNT, language).formatted(won(amount));
        return pick(DUE_REMINDER_BODY, language).formatted(name, dueDate, leadDays, amountText);
    }

    static List<String> warningCodes() {
        return List.of(LATE_PENALTY, SEIZURE, STAY_EXTENSION, OVERDUE);
    }
}
