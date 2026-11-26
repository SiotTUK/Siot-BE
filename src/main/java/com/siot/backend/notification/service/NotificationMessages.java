package com.siot.backend.notification.service;

import com.siot.backend.global.common.SupportedLanguages;

import java.util.Map;

/**
 * 알림 제목·본문의 언어별 문구 (COM-02, NTF-05). 회원의 화면 언어(app_user.language)로 만든다.
 * 각 값은 {@link String#format} 형식이며, 인자 순서는 필드 주석을 따른다.
 */
public record NotificationMessages(
        /** (leadDays) */
        String stayExpiryTitle,
        /** (expiryDate, leadDays) */
        String stayExpiryBody,
        String stayExtensionStartTitle,
        /** (expiryDate) */
        String stayExtensionStartBody,
        /** (leadDays) */
        String registrationTitle,
        /** (deadline, leadDays) */
        String registrationBody,
        String residenceReportName,
        String overseasKoreanReportName,
        /** (reportName, leadDays) */
        String residenceReportTitle,
        /** (moveDate, days, reportName, deadline, leadDays) */
        String residenceReportBody,
        /** (taxTitle) */
        String taxStartTitle,
        /** (taxTitle, startsOn, endsOn) */
        String taxStartBody,
        /** (taxTitle, leadDays) */
        String taxDueTitle,
        /** (taxTitle, endsOn, leadDays) */
        String taxDueBody,
        /** (eventTitle) */
        String eventStartTitle,
        /** (eventTitle, startText) */
        String eventStartBody,
        /** (eventTitle) */
        String eventDeadlineTitle,
        /** (eventTitle, deadlineText) */
        String eventDeadlineBody,
        /** (eventTitle) */
        String eventChangedTitle,
        /** (eventTitle, scheduleText) */
        String eventChangedBody,
        /** (guideTitle) */
        String noticeChangedTitle,
        /** (guideTitle) */
        String noticeChangedBody,
        /** 행사 제목이 없을 때 */
        String defaultEventTitle
) {
    private static final Map<String, NotificationMessages> BY_LANGUAGE = Map.of(
            "ko", new NotificationMessages(
                    "체류기간 만료 D-%d 안내",
                    "체류기간 만료일(%s)까지 %d일 남았습니다. 출입국관리사무소 또는 하이코리아에서 체류기간 연장 허가를 신청하세요.",
                    "체류기간 연장 신청 시작 안내",
                    "오늘부터 체류기간 연장 허가를 신청할 수 있습니다 (체류 만료일 %s). 하이코리아(hikorea.go.kr)에서 방문 예약 또는 전자민원으로 신청하세요.",
                    "외국인등록 마감 D-%d 안내",
                    "입국 후 90일 외국인등록 마감일(%s)까지 %d일 남았습니다. 관할 출입국·외국인관서에 방문하여 등록을 완료해주세요.",
                    "체류지 변경 신고",
                    "거소 이전 신고",
                    "%s 마감 D-%d 안내",
                    "이사일(%s)부터 %d일 이내인 %s 마감일(%s)까지 %d일 남았습니다. 새 주소지 주민센터나 출입국·외국인관서, 하이코리아에서 신고해주세요.",
                    "[%s] 오늘부터 납부 기간입니다",
                    "%s 납부 기간(%s ~ %s)이 오늘 시작됐습니다. 위택스(wetax.go.kr) 또는 금융기관에서 기한 내 납부해주세요.",
                    "[%s] 납부 마감 D-%d 안내",
                    "%s 납부 마감일(%s)까지 %d일 남았습니다. 위택스(wetax.go.kr) 또는 금융기관에서 기한 내 납부해주세요.",
                    "[%s] 행사가 곧 시작됩니다",
                    "알림을 신청한 행사 '%s'가 %s에 시작됩니다. 일정과 장소를 확인하세요.",
                    "[%s] 신청 마감 D-1 안내",
                    "알림을 신청한 행사 '%s'의 신청 마감은 %s입니다. 참여하려면 마감 전에 신청하세요.",
                    "[%s] 행사 일정이 변경되었습니다",
                    "알림을 신청한 행사 '%s'의 일정이 %s(으)로 변경되었습니다. 상세 페이지에서 확인하세요.",
                    "[안내 내용 변경] %s",
                    "'%s'의 안내 내용(준비물, 수수료, 신청 기한 등)이 변경되었습니다. 변경된 최신 정보를 확인해 주세요.",
                    "시흥시 문화행사"),
            "en", new NotificationMessages(
                    "Stay expiry in %d day(s)",
                    "Your stay expires on %s (%d day(s) left). Apply for a stay extension at an immigration office or on HiKorea.",
                    "You can now apply for a stay extension",
                    "Starting today, you can apply to extend your stay (stay expiry date: %s). Book a visit or apply online on HiKorea (hikorea.go.kr).",
                    "Alien registration due in %d day(s)",
                    "The deadline for alien registration (within 90 days of entry) is %s, %d day(s) from now. Please visit your local immigration office to register.",
                    "Report of change of residence",
                    "Report of change of domicile",
                    "%s due in %d day(s)",
                    "You moved on %s. The %d-day deadline for the %s is %s, %d day(s) from now. Report it at your new community service center, an immigration office, or on HiKorea.",
                    "[%s] Payment period starts today",
                    "The payment period for %s (%s to %s) started today. Please pay on time via Wetax (wetax.go.kr) or at a bank.",
                    "[%s] Payment due in %d day(s)",
                    "The payment deadline for %s is %s, %d day(s) from now. Please pay on time via Wetax (wetax.go.kr) or at a bank.",
                    "[%s] Event starting soon",
                    "The event '%s' you subscribed to starts on %s. Check the schedule and venue.",
                    "[%s] Application closes tomorrow",
                    "Applications for the event '%s' you subscribed to close on %s. Apply before the deadline to take part.",
                    "[%s] Event schedule changed",
                    "The schedule of the event '%s' you subscribed to has changed to %s. See the details page.",
                    "[Information updated] %s",
                    "The information for '%s' (documents, fees, deadlines, etc.) has been updated. Please check the latest details.",
                    "Siheung cultural event"),
            "zh", new NotificationMessages(
                    "居留期满 D-%d 提醒",
                    "您的居留期满日为%s，还剩%d天。请到出入境管理事务所或通过HiKorea申请延长居留期限。",
                    "可以开始申请延长居留期限",
                    "从今天起可以申请延长居留期限（居留期满日：%s）。请在HiKorea（hikorea.go.kr）预约访问或在线申请。",
                    "外国人登录截止 D-%d 提醒",
                    "入境后90天内的外国人登录截止日为%s，还剩%d天。请前往管辖出入境·外国人管理机关完成登录。",
                    "滞留地变更申报",
                    "居所迁移申报",
                    "%s截止 D-%d 提醒",
                    "您于%s搬家，%d天内须完成的%s截止日为%s，还剩%d天。请到新住址的居民中心、出入境·外国人管理机关或通过HiKorea申报。",
                    "[%s] 今天开始缴纳",
                    "%s的缴纳期（%s ~ %s）今天开始。请通过Wetax（wetax.go.kr）或金融机构按期缴纳。",
                    "[%s] 缴纳截止 D-%d 提醒",
                    "%s的缴纳截止日为%s，还剩%d天。请通过Wetax（wetax.go.kr）或金融机构按期缴纳。",
                    "[%s] 活动即将开始",
                    "您订阅提醒的活动“%s”将于%s开始。请确认日程和地点。",
                    "[%s] 报名明天截止",
                    "您订阅提醒的活动“%s”报名截止时间为%s。如需参加，请在截止前报名。",
                    "[%s] 活动日程已变更",
                    "您订阅提醒的活动“%s”日程已变更为%s。请在详情页确认。",
                    "[信息变更] %s",
                    "“%s”的指南内容（所需材料、手续费、申请期限等）已变更。请确认最新信息。",
                    "始兴市文化活动"),
            "vi", new NotificationMessages(
                    "Còn %d ngày đến hạn lưu trú",
                    "Thời gian lưu trú của bạn hết hạn vào %s (còn %d ngày). Hãy xin gia hạn lưu trú tại văn phòng xuất nhập cảnh hoặc trên HiKorea.",
                    "Đã có thể nộp đơn gia hạn lưu trú",
                    "Từ hôm nay bạn có thể nộp đơn gia hạn thời gian lưu trú (ngày hết hạn: %s). Hãy đặt lịch hoặc nộp trực tuyến trên HiKorea (hikorea.go.kr).",
                    "Còn %d ngày đến hạn đăng ký người nước ngoài",
                    "Hạn đăng ký người nước ngoài (trong vòng 90 ngày sau khi nhập cảnh) là %s, còn %d ngày. Hãy đến cơ quan xuất nhập cảnh quản lý khu vực để đăng ký.",
                    "khai báo thay đổi nơi lưu trú",
                    "khai báo chuyển nơi cư trú",
                    "Còn %2$d ngày đến hạn %1$s",
                    "Bạn chuyển nhà ngày %s. Hạn %d ngày để %s là %s, còn %d ngày. Hãy khai báo tại trung tâm hành chính nơi ở mới, cơ quan xuất nhập cảnh hoặc trên HiKorea.",
                    "[%s] Hôm nay bắt đầu kỳ nộp thuế",
                    "Kỳ nộp %s (%s ~ %s) bắt đầu hôm nay. Hãy nộp đúng hạn qua Wetax (wetax.go.kr) hoặc tại ngân hàng.",
                    "[%s] Còn %d ngày đến hạn nộp",
                    "Hạn nộp %s là %s, còn %d ngày. Hãy nộp đúng hạn qua Wetax (wetax.go.kr) hoặc tại ngân hàng.",
                    "[%s] Sự kiện sắp bắt đầu",
                    "Sự kiện '%s' bạn đã đăng ký nhận thông báo sẽ bắt đầu vào %s. Hãy kiểm tra lịch và địa điểm.",
                    "[%s] Ngày mai hết hạn đăng ký",
                    "Hạn đăng ký sự kiện '%s' bạn đã đăng ký nhận thông báo là %s. Hãy đăng ký trước hạn nếu muốn tham gia.",
                    "[%s] Lịch sự kiện đã thay đổi",
                    "Lịch của sự kiện '%s' bạn đã đăng ký nhận thông báo đã đổi thành %s. Hãy xem trang chi tiết.",
                    "[Thông tin thay đổi] %s",
                    "Thông tin hướng dẫn của '%s' (giấy tờ, lệ phí, thời hạn, v.v.) đã thay đổi. Hãy kiểm tra thông tin mới nhất.",
                    "Sự kiện văn hóa Siheung")
    );

    /** 지원하지 않거나 비어 있으면 한국어 */
    public static NotificationMessages forLanguage(String language) {
        return BY_LANGUAGE.get(SupportedLanguages.orDefault(language));
    }
}
