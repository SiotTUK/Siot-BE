package com.siot.backend.notification.service;

import com.siot.backend.global.common.PageResponse;
import com.siot.backend.global.exception.BusinessException;
import com.siot.backend.global.exception.ErrorCode;
import com.siot.backend.checklist.domain.Guide;
import com.siot.backend.checklist.domain.GuideTranslation;
import com.siot.backend.checklist.repository.GuideTranslationRepository;
import com.siot.backend.member.domain.AppUser;
import com.siot.backend.member.repository.AppUserRepository;
import com.siot.backend.notification.domain.EmailDeliveryStatus;
import com.siot.backend.notification.domain.Notification;
import com.siot.backend.notification.domain.NotificationPreference;
import com.siot.backend.notification.dto.NotificationPreferenceResponse;
import com.siot.backend.notification.dto.NotificationPreferenceUpdateRequest;
import com.siot.backend.notification.dto.NotificationResponse;
import com.siot.backend.notification.repository.NotificationPreferenceRepository;
import com.siot.backend.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final AppUserRepository appUserRepository;
    private final GuideTranslationRepository guideTranslationRepository;

    private static final List<String> DEFAULT_KINDS = List.of("STAY_EXPIRY", "TAX", "EVENT", "NOTICE_CHANGED");

    /**
     * 회원 알림 목록 페이징 조회 (NTF-04)
     */
    public PageResponse<NotificationResponse> getNotifications(Long userId, Pageable pageable) {
        Page<Notification> page = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return PageResponse.from(page, NotificationResponse::from);
    }

    /**
     * 안 읽은 알림 개수 조회
     */
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadAtIsNull(userId);
    }

    /**
     * 알림 단건 읽음 처리 (NTF-04)
     */
    @Transactional
    public NotificationResponse markRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .filter(n -> n.getUserId().equals(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));

        if (notification.getReadAt() == null) {
            notification.markRead();
        }
        return NotificationResponse.from(notification);
    }

    /**
     * 알림 전체 읽음 처리
     */
    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllAsReadByUserId(userId, OffsetDateTime.now());
    }

    /**
     * 알림 수신 설정 목록 조회
     */
    public List<NotificationPreferenceResponse> getPreferences(Long userId) {
        List<NotificationPreference> userPrefs = preferenceRepository.findByUserId(userId);
        Map<String, NotificationPreference> prefMap = new HashMap<>();
        for (NotificationPreference pref : userPrefs) {
            prefMap.put(pref.getKind(), pref);
        }

        List<NotificationPreferenceResponse> responses = new ArrayList<>();
        for (String kind : DEFAULT_KINDS) {
            if (prefMap.containsKey(kind)) {
                responses.add(NotificationPreferenceResponse.from(prefMap.get(kind)));
            } else {
                int defaultLeadDays = switch (kind) {
                    case "STAY_EXPIRY" -> 30;
                    case "TAX" -> 7;
                    case "EVENT" -> 1;
                    default -> 0;
                };
                responses.add(new NotificationPreferenceResponse(kind, true, defaultLeadDays));
            }
        }
        return responses;
    }

    /**
     * 알림 수신 설정 변경 (NTF-03)
     */
    @Transactional
    public NotificationPreferenceResponse updatePreference(Long userId, NotificationPreferenceUpdateRequest request) {
        String kind = request.kind().toUpperCase();
        int leadDays = request.leadDays() != null ? request.leadDays() : 0;

        NotificationPreference preference = preferenceRepository.findByKindAndUserId(kind, userId)
                .map(existing -> {
                    existing.update(request.enabled(), leadDays);
                    return existing;
                })
                .orElseGet(() -> preferenceRepository.save(
                        NotificationPreference.of(kind, userId, request.enabled(), leadDays)
                ));

        return NotificationPreferenceResponse.from(preference);
    }

    /**
     * NTF-07: 안내 내용 변경 알림 발송 (대상 회원에게 인앱/이메일 알림 생성)
     */
    @Transactional
    public void createNoticeChangedNotifications(Guide guide) {
        if (guide == null || guide.getNoticeChangedAt() == null) {
            return;
        }

        List<AppUser> allUsers = appUserRepository.findAll();

        for (AppUser user : allUsers) {
            if (isPreferenceDisabled(user.getId(), "NOTICE_CHANGED")) {
                continue;
            }

            // 변경 시점마다 1회 발송되도록 타임스탬프 결합
            String sourceKey = String.format("notice_changed:%d:%d:%d",
                    guide.getId(), user.getId(), guide.getNoticeChangedAt().toEpochSecond());

            if (notificationRepository.existsByUserIdAndSourceKey(user.getId(), sourceKey)) {
                continue;
            }

            String titleText = guide.getCode();
            GuideTranslation tr = guideTranslationRepository.findByGuideIdAndLanguage(guide.getId(), user.getLanguage())
                    .or(() -> guideTranslationRepository.findByGuideIdAndLanguage(guide.getId(), "ko"))
                    .orElse(null);
            if (tr != null) {
                titleText = tr.getTitle();
            }

            String title = String.format("[안내 내용 변경] %s", titleText);
            String body = String.format("'%s'의 중요 안내 내용(준비물, 수수료, 신청 기한 등)이 변경되었습니다. 변경된 최신 정보를 확인해 주세요.", titleText);
            EmailDeliveryStatus emailStatus = user.hasVerifiedEmail() ? EmailDeliveryStatus.PENDING : null;

            Notification notification = Notification.create(
                    user.getId(),
                    "NOTICE_CHANGED",
                    null,
                    guide.getId(),
                    null,
                    null,
                    sourceKey,
                    1,
                    OffsetDateTime.now(),
                    user.getLanguage(),
                    title,
                    body,
                    emailStatus
            );
            notificationRepository.save(notification);
        }
    }

    private boolean isPreferenceDisabled(Long userId, String kind) {
        return preferenceRepository.findByKindAndUserId(kind, userId)
                .map(pref -> !pref.isEnabled())
                .orElse(false);
    }
}
