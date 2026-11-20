package com.siot.backend.notification.service;

import com.siot.backend.global.common.PageResponse;
import com.siot.backend.global.exception.BusinessException;
import com.siot.backend.global.exception.ErrorCode;
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

    /** CHECKLIST_DEADLINE: 외국인등록 등 체크리스트 마감 알림 (NTF-08) */
    private static final List<String> DEFAULT_KINDS =
            List.of("STAY_EXPIRY", "CHECKLIST_DEADLINE", "TAX", "EVENT", "NOTICE_CHANGED");

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
                    case "CHECKLIST_DEADLINE", "TAX" -> 7;
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
}
