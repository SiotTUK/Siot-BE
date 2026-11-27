package com.siot.backend.checklist.domain;

import com.siot.backend.global.common.SupportedLanguages;

import java.util.Map;

/**
 * CHK-08 체크리스트 경고 종류와 4개 언어 문구 (COM-02). 응답에는 종류 코드(warningType)와
 * 요청 언어의 문구(warningMessage)를 함께 준다. 코드는 프론트엔드가 분기에 쓰므로 바꾸지 않는다.
 */
public enum ChecklistWarning {

    /** 마감 기한 초과 (모든 항목) */
    OVERDUE(
            "마감 기한이 초과되었습니다. 즉시 확인 후 조치하세요.",
            "The deadline has passed. Please check and take action right away.",
            "已超过截止期限。请立即确认并处理。",
            "Đã quá hạn. Vui lòng kiểm tra và xử lý ngay."),
    /** 외국인등록·거소신고(CL-01·CL-02) 마감 D-30 이내 */
    REGISTRATION_DUE(
            "외국인등록 기한이 30일 이내로 임박했습니다. 기한 초과 시 과태료 및 불이익이 발생합니다.",
            "Your foreigner registration deadline is within 30 days. Missing it can lead to a fine and other disadvantages.",
            "外国人登录期限已不足30天。逾期将被处以罚款并可能产生其他不利影响。",
            "Hạn đăng ký người nước ngoài còn dưới 30 ngày. Quá hạn có thể bị phạt tiền và chịu bất lợi khác."),
    /** 체류지 변경 신고(LC-01-1) 마감 D-7 이내 */
    RESIDENCE_REPORT_DUE(
            "체류지 변경 신고 마감(전입 후 15일 이내) 기한이 7일 이내로 임박했습니다. 기한 초과 시 과태료가 부과됩니다.",
            "The deadline to report your change of residence (within 15 days of moving) is within 7 days. Missing it results in a fine.",
            "居留地变更申报期限（迁入后15日内）已不足7天。逾期将被处以罚款。",
            "Hạn khai báo thay đổi nơi cư trú (trong vòng 15 ngày sau khi chuyển đến) còn dưới 7 ngày. Quá hạn sẽ bị phạt tiền.");

    private final Map<String, String> messages;

    ChecklistWarning(String ko, String en, String zh, String vi) {
        this.messages = Map.of("ko", ko, "en", en, "zh", zh, "vi", vi);
    }

    /** 요청 언어의 문구 (지원하지 않는 언어면 한국어) */
    public String message(String language) {
        return messages.get(SupportedLanguages.orDefault(language));
    }
}
