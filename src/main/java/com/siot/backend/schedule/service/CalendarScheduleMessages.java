package com.siot.backend.schedule.service;

import java.util.Map;

/** 개인 일정에 사용하는 화면 언어별 문구. */
record CalendarScheduleMessages(
        String extensionTitle,
        String extensionWindow,
        String expiryTitle,
        String expiryDescription,
        String carTaxTitle,
        String carTaxDescription,
        String propertyTaxTitle,
        String propertyTaxDescription
) {
    private static final Map<String, CalendarScheduleMessages> BY_LANGUAGE = Map.of(
            "ko", new CalendarScheduleMessages(
                    "체류기간 연장 신청 시작일",
                    "%s부터 체류 만료일(%s)까지 체류기간 연장을 신청할 수 있습니다.",
                    "체류기간 만료일",
                    "체류기간 연장 신청은 %s부터 가능합니다.",
                    "자동차세 %d기분 납부",
                    "자차 보유자 대상 자동차세 납부 기간입니다.",
                    "재산세 %d기분 납부",
                    "주택 소유자 대상 재산세 납부 기간입니다."),
            "en", new CalendarScheduleMessages(
                    "First day to apply for a stay extension",
                    "You can apply to extend your stay from %s through your stay expiry date (%s).",
                    "Stay expiry date",
                    "You can apply to extend your stay starting on %s.",
                    "Vehicle tax payment (installment %d)",
                    "This vehicle tax payment period applies to vehicle owners.",
                    "Property tax payment (installment %d)",
                    "This property tax payment period applies to homeowners."),
            "zh", new CalendarScheduleMessages(
                    "居留期限延长申请开始日",
                    "可从%s起至居留期满日（%s）申请延长居留期限。",
                    "居留期满日",
                    "可从%s起申请延长居留期限。",
                    "汽车税缴纳（第%d期）",
                    "本汽车税缴纳期适用于车辆所有人。",
                    "财产税缴纳（第%d期）",
                    "本财产税缴纳期适用于房屋所有人。"),
            "vi", new CalendarScheduleMessages(
                    "Ngày bắt đầu nộp đơn gia hạn thời gian lưu trú",
                    "Bạn có thể nộp đơn gia hạn thời gian lưu trú từ ngày %s đến hết ngày lưu trú (%s).",
                    "Ngày hết hạn lưu trú",
                    "Bạn có thể nộp đơn gia hạn thời gian lưu trú từ ngày %s.",
                    "Nộp thuế ô tô (đợt %d)",
                    "Kỳ nộp thuế ô tô này áp dụng cho chủ sở hữu ô tô.",
                    "Nộp thuế tài sản (đợt %d)",
                    "Kỳ nộp thuế tài sản này áp dụng cho chủ sở hữu nhà ở.")
    );

    /** language는 SupportedLanguages.orDefault에서 정규화한다. */
    static CalendarScheduleMessages forLanguage(String language) {
        return BY_LANGUAGE.get(language);
    }
}
