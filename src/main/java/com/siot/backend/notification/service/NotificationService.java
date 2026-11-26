package com.siot.backend.notification.service;

import com.siot.backend.global.common.PageResponse;
import com.siot.backend.global.exception.BusinessException;
import com.siot.backend.global.exception.ErrorCode;
import com.siot.backend.checklist.domain.ChecklistProgress;
import com.siot.backend.checklist.domain.Guide;
import com.siot.backend.checklist.domain.GuideTranslation;
import com.siot.backend.checklist.domain.MemberLifeChange;
import com.siot.backend.checklist.domain.ProgressStatus;
import com.siot.backend.checklist.repository.ChecklistProgressRepository;
import com.siot.backend.checklist.repository.GuideRepository;
import com.siot.backend.checklist.repository.GuideTranslationRepository;
import com.siot.backend.checklist.repository.MemberLifeChangeRepository;
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

import java.time.Clock;
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
    private final ChecklistProgressRepository checklistProgressRepository;
    private final MemberLifeChangeRepository memberLifeChangeRepository;
    private final GuideRepository guideRepository;
    /** 한국 시간 (ClockConfig) */
    private final Clock clock;

    /** CHECKLIST_DEADLINE: 외국인등록 등 체크리스트 마감 알림 (NTF-08) */
    private static final List<String> DEFAULT_KINDS =
            List.of("STAY_EXPIRY", "CHECKLIST_DEADLINE", "TAX", "EVENT", "NOTICE_CHANGED");

    /**
     * 회원 알림 목록 페이징 조회 (NTF-04)
     */
    public PageResponse<NotificationResponse> getNotifications(Long userId, Pageable pageable) {
        Page<Notification> page = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        // 항목 상세로 가려면 code 가 필요하다 (NTF-04): 이 페이지의 항목을 한 번에 읽는다
        List<Long> guideIds = page.getContent().stream()
                .map(Notification::getGuideId).filter(Objects::nonNull).distinct().toList();
        Map<Long, Guide> guides = guideIds.isEmpty() ? Map.of()
                : guideRepository.findAllById(guideIds).stream()
                .collect(java.util.stream.Collectors.toMap(Guide::getId, g -> g));
        // Map.of() 와 toMap 결과는 get(null) 에서 NPE 가 날 수 있어 guideId 없는 알림은 따로 처리한다
        return PageResponse.from(page, n -> NotificationResponse.from(n,
                n.getGuideId() == null ? null : guides.get(n.getGuideId())));
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
        Guide guide = notification.getGuideId() == null ? null
                : guideRepository.findById(notification.getGuideId()).orElse(null);
        return NotificationResponse.from(notification, guide);
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
        // 알림 종류는 정해진 5가지만 (스케줄러가 읽는 값). 모르는 값을 저장하면 아무 효과 없이 쌓이고, 30자를 넘으면 DB 오류
        String kind = request.kind() == null ? "" : request.kind().strip().toUpperCase(Locale.ROOT);
        if (!DEFAULT_KINDS.contains(kind)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "kind 는 " + String.join(", ", DEFAULT_KINDS) + " 중 하나입니다.");
        }
        if (request.enabled() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "enabled 는 필수입니다.");
        }
        int leadDays = request.leadDays() != null ? request.leadDays() : 0;
        if (leadDays < 0 || leadDays > 365) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "leadDays 는 0~365 입니다.");
        }

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
     * NTF-07: 안내 내용 변경 알림 (인앱 + 인증된 이메일).
     * 대상은 기준 날짜를 입력했고 그 항목을 완료·해당 없음으로 표시하지 않은 회원이다.
     * <ul>
     *   <li>체크리스트(CL-xx): 입국일을 입력한 회원. 1회성 항목의 완료 기록으로 거른다</li>
     *   <li>생활 변화 할 일(LC-xx-n): 그 생활 변화(이사일 등)를 등록한 회원. 그 회차(기준일)의 완료 기록으로 거른다</li>
     *   <li>생활수칙: 기준 날짜가 없으므로 알림을 보내지 않는다 (상세 화면의 변경 표시만)</li>
     * </ul>
     */
    @Transactional
    public void createNoticeChangedNotifications(Guide guide) {
        if (guide == null || guide.getNoticeChangedAt() == null) {
            return;
        }

        for (NoticeTarget target : noticeTargets(guide)) {
            AppUser user = target.user();
            if (isPreferenceDisabled(user.getId(), "NOTICE_CHANGED")) {
                continue;
            }
            if (isChecklistClosed(user.getId(), guide.getId(), target.periodKey())) {
                continue;
            }

            // 변경 시점마다 1회 발송되도록 타임스탬프 결합
            String sourceKey = String.format("notice_changed:%d:%d:%d",
                    guide.getId(), user.getId(), guide.getNoticeChangedAt().toEpochSecond());
            if (notificationRepository.existsByUserIdAndSourceKey(user.getId(), sourceKey)) {
                continue;
            }

            String titleText = guideTranslationRepository.findByGuideIdAndLanguage(guide.getId(), user.getLanguage())
                    .or(() -> guideTranslationRepository.findByGuideIdAndLanguage(guide.getId(), "ko"))
                    .map(GuideTranslation::getTitle)
                    .orElse(guide.getCode());
            NotificationMessages m = NotificationMessages.forLanguage(user.getLanguage());
            EmailDeliveryStatus emailStatus = user.hasVerifiedEmail() ? EmailDeliveryStatus.PENDING : null;

            notificationRepository.save(Notification.create(
                    user.getId(), "NOTICE_CHANGED", null, guide.getId(), null, null, sourceKey, 1,
                    OffsetDateTime.now(clock), user.getLanguage(),
                    String.format(m.noticeChangedTitle(), titleText),
                    String.format(m.noticeChangedBody(), titleText),
                    emailStatus));
        }
    }

    /** 알림 대상 회원과 완료 여부를 볼 회차 키 */
    private record NoticeTarget(AppUser user, String periodKey) {
    }

    private List<NoticeTarget> noticeTargets(Guide guide) {
        if ("CHECKLIST".equals(guide.getKind())) {
            return appUserRepository.findByEntryDateIsNotNull().stream()
                    .map(user -> new NoticeTarget(user, ChecklistProgress.ONCE))
                    .toList();
        }
        if ("LIFE_CHANGE_TASK".equals(guide.getKind()) && guide.getLifeChangeCode() != null) {
            List<NoticeTarget> targets = new ArrayList<>();
            for (MemberLifeChange change : memberLifeChangeRepository.findByLifeChangeCode(guide.getLifeChangeCode())) {
                if (change.getBaseDate() == null) {
                    continue;
                }
                // 생활 변화 할 일의 회차 키 = 기준일
                appUserRepository.findById(change.getUserId())
                        .ifPresent(user -> targets.add(new NoticeTarget(user, change.getBaseDate().toString())));
            }
            return targets;
        }
        return List.of();
    }

    /** 그 회차를 완료(DONE) 또는 해당 없음(NOT_APPLICABLE)으로 표시했으면 true */
    private boolean isChecklistClosed(Long userId, Long guideId, String periodKey) {
        return checklistProgressRepository
                .findByUserIdAndGuideIdAndPeriodKey(userId, guideId, periodKey)
                .map(p -> p.getStatus() == ProgressStatus.DONE || p.getStatus() == ProgressStatus.NOT_APPLICABLE)
                .orElse(false);
    }

    private boolean isPreferenceDisabled(Long userId, String kind) {
        return preferenceRepository.findByKindAndUserId(kind, userId)
                .map(pref -> !pref.isEnabled())
                .orElse(false);
    }
}
