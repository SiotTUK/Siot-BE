package com.siot.backend.translation.bill;

import com.siot.backend.global.common.SupportedLanguages;
import com.siot.backend.member.domain.AppUser;
import com.siot.backend.member.repository.AppUserRepository;
import com.siot.backend.notification.domain.EmailDeliveryStatus;
import com.siot.backend.notification.domain.Notification;
import com.siot.backend.notification.dto.NotificationResponse;
import com.siot.backend.notification.repository.NotificationPreferenceRepository;
import com.siot.backend.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * RUL-07 고지서 납부 마감 알림. 최근 번역에서 마감 알림을 켠 고지서의 납부 기한 D-7·D-1 에 알림을 만든다.
 * 발송(인앱·이메일)은 NotificationScheduler 의 발송 작업이 한다. 알림 설정은 정기 세금과 같은 TAX 를 따른다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BillDueNotifier {

    /** 정기 세금 납부 알림(NTF-01)과 같은 D-7, D-1 */
    static final List<Integer> LEAD_DAYS = List.of(7, 1);
    static final String PREFERENCE_KIND = "TAX";

    private final BillTranslationRepository billTranslationRepository;
    private final AppUserRepository appUserRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    /** 한국 시간 (ClockConfig) */
    private final Clock clock;

    /** 매일 오전 9시 (다른 마감 알림과 같은 시각) */
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    public void run() {
        int created = generate();
        log.info("Bill due notifications created={}", created);
    }

    /** @return 새로 만든 알림 수 */
    @Transactional
    public int generate() {
        LocalDate today = LocalDate.now(clock);
        int created = 0;
        for (int leadDays : LEAD_DAYS) {
            LocalDate dueDate = today.plusDays(leadDays);
            for (BillTranslation bill : billTranslationRepository.findByReminderTrueAndDueDate(dueDate)) {
                if (notify(bill, leadDays)) {
                    created++;
                }
            }
        }
        return created;
    }

    private boolean notify(BillTranslation bill, int leadDays) {
        Long userId = bill.getUserId();
        boolean disabled = preferenceRepository.findByKindAndUserId(PREFERENCE_KIND, userId)
                .map(pref -> !pref.isEnabled())
                .orElse(false);
        if (disabled) {
            return false;
        }
        String sourceKey = String.format("bill_due:%d:%d:%s", bill.getId(), leadDays, bill.getDueDate());
        if (notificationRepository.existsByUserIdAndSourceKey(userId, sourceKey)) {
            return false;
        }
        AppUser user = appUserRepository.findById(userId).orElse(null);
        if (user == null) {
            return false;
        }
        // 알림은 회원의 지금 화면 언어로 (번역 결과 언어와 다를 수 있다)
        String language = SupportedLanguages.orDefault(user.getLanguage());
        String name = name(bill, language);
        EmailDeliveryStatus emailStatus = user.hasVerifiedEmail() ? EmailDeliveryStatus.PENDING : null;
        notificationRepository.save(Notification.create(
                userId, NotificationResponse.KIND_BILL_DUE, null, null, null, null, sourceKey, 1,
                OffsetDateTime.now(clock), language,
                BillMessages.dueReminderTitle(name, leadDays, language),
                BillMessages.dueReminderBody(name, bill.getDueDate(), leadDays, bill.getAmount(), language),
                emailStatus).linkBillTranslation(bill.getId()));
        return true;
    }

    /** 세목(원문, 예: 자동차세)이 있으면 그것, 없으면 서식 이름 (사용자 언어) */
    private static String name(BillTranslation bill, String language) {
        String taxType = bill.getTaxType();
        if (taxType != null && !taxType.isBlank()) {
            String stripped = taxType.strip();
            return stripped.length() > 40 ? stripped.substring(0, 40) : stripped;
        }
        return BillMessages.documentLabel(bill.getDocumentType(), language);
    }
}
