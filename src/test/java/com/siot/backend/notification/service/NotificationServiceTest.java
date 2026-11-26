package com.siot.backend.notification.service;

import com.siot.backend.global.common.PageResponse;
import com.siot.backend.notification.domain.Notification;
import com.siot.backend.notification.domain.NotificationPreference;
import com.siot.backend.notification.dto.NotificationPreferenceResponse;
import com.siot.backend.notification.dto.NotificationPreferenceUpdateRequest;
import com.siot.backend.notification.dto.NotificationResponse;
import com.siot.backend.notification.repository.NotificationPreferenceRepository;
import com.siot.backend.notification.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationPreferenceRepository preferenceRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    @DisplayName("알림 목록 페이징 및 안 읽은 개수 조회 검증")
    void getNotifications_and_unreadCount() {
        // given
        Long userId = 1L;
        Notification n1 = Notification.create(userId, "STAY_EXPIRY", null, null, null, null,
                "stay:1", 1, OffsetDateTime.now(), "ko", "만료 안내", "30일 전", null);
        PageRequest pageable = PageRequest.of(0, 10);

        given(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable))
                .willReturn(new PageImpl<>(List.of(n1), pageable, 1));
        given(notificationRepository.countByUserIdAndReadAtIsNull(userId))
                .willReturn(1L);

        // when
        PageResponse<NotificationResponse> result = notificationService.getNotifications(userId, pageable);
        long unread = notificationService.getUnreadCount(userId);

        // then
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).title()).isEqualTo("만료 안내");
        assertThat(unread).isEqualTo(1L);
    }

    @Test
    @DisplayName("알림 단건 읽음 처리 검증")
    void markRead_success() {
        // given
        Long userId = 1L;
        Long notificationId = 10L;
        Notification n = Notification.create(userId, "TAX", null, null, null, null,
                "tax:1", 1, OffsetDateTime.now(), "ko", "세금 납부", "납부 기간", null);

        given(notificationRepository.findById(notificationId)).willReturn(Optional.of(n));

        // when
        NotificationResponse res = notificationService.markRead(userId, notificationId);

        // then
        assertThat(res.read()).isTrue();
    }

    @Test
    @DisplayName("알림 수신 설정 변경 검증")
    void updatePreference_success() {
        // given
        Long userId = 1L;
        NotificationPreferenceUpdateRequest req = new NotificationPreferenceUpdateRequest("STAY_EXPIRY", false, 14);

        given(preferenceRepository.findByKindAndUserId("STAY_EXPIRY", userId))
                .willReturn(Optional.empty());
        given(preferenceRepository.save(any(NotificationPreference.class)))
                .willAnswer(inv -> inv.getArgument(0));

        // when
        NotificationPreferenceResponse res = notificationService.updatePreference(userId, req);

        // then
        assertThat(res.kind()).isEqualTo("STAY_EXPIRY");
        assertThat(res.enabled()).isFalse();
        assertThat(res.leadDays()).isEqualTo(14);
    }

    @Test
    @DisplayName("알림 설정: 모르는 종류·범위 밖 일수는 400 (저장하지 않는다)")
    void updatePreference_rejectsUnknownKindAndBadLeadDays() {
        for (NotificationPreferenceUpdateRequest bad : List.of(
                new NotificationPreferenceUpdateRequest("PROMOTION", true, 0),
                new NotificationPreferenceUpdateRequest("x".repeat(40), true, 0),
                new NotificationPreferenceUpdateRequest("TAX", true, -1),
                new NotificationPreferenceUpdateRequest("TAX", true, 400))) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> notificationService.updatePreference(1L, bad))
                    .isInstanceOf(com.siot.backend.global.exception.BusinessException.class)
                    .extracting(e -> ((com.siot.backend.global.exception.BusinessException) e).getErrorCode())
                    .isEqualTo(com.siot.backend.global.exception.ErrorCode.INVALID_INPUT);
        }
        org.mockito.Mockito.verify(preferenceRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("NTF-03 알림 시점: 고를 수 있는 값 중 하나 이상만 저장하고, 기본은 전부")
    void updatePreference_leadDaysList() {
        given(preferenceRepository.findByKindAndUserId("TAX", 1L)).willReturn(Optional.empty());
        given(preferenceRepository.save(any(NotificationPreference.class))).willAnswer(inv -> inv.getArgument(0));

        NotificationPreferenceResponse res = notificationService.updatePreference(1L,
                new NotificationPreferenceUpdateRequest("TAX", true, null, null, List.of(1)));
        assertThat(res.leadDaysList()).containsExactly(1);
        assertThat(res.availableLeadDays()).containsExactly(7, 1);

        given(preferenceRepository.findByUserId(1L)).willReturn(List.of());
        assertThat(notificationService.getPreferences(1L))
                .filteredOn(p -> p.kind().equals("STAY_EXPIRY")).singleElement()
                .satisfies(p -> assertThat(p.leadDaysList()).containsExactly(120, 60, 30, 14, 7, 1));

        for (NotificationPreferenceUpdateRequest bad : List.of(
                new NotificationPreferenceUpdateRequest("TAX", true, null, null, List.of()),
                new NotificationPreferenceUpdateRequest("TAX", true, null, null, List.of(3)),
                new NotificationPreferenceUpdateRequest("EVENT", true, null, null, List.of(1)))) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> notificationService.updatePreference(1L, bad))
                    .extracting(e -> ((com.siot.backend.global.exception.BusinessException) e).getErrorCode())
                    .isEqualTo(com.siot.backend.global.exception.ErrorCode.INVALID_INPUT);
        }
    }
}
