package com.siot.backend.notification.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "notification_preference")
@IdClass(NotificationPreferenceId.class)
public class NotificationPreference {

    @Id
    @Column(length = 30)
    private String kind;

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "lead_days", nullable = false)
    private int leadDays = 0;

    /**
     * NTF-03 받을 사전 알림 시점 (D-n 여러 개). null 이면 그 종류의 기본 시점 전부 ({@link NotificationLeadDays}).
     * 예전 leadDays(하나)는 발송에 쓰지 않는다.
     */
    @Column(name = "lead_days_list", columnDefinition = "integer[]")
    private List<Integer> leadDaysList;

    /** NTF-03·05 이 종류의 알림을 이메일로도 받을지 (인앱 알림은 enabled 가 정한다) */
    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled = true;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static NotificationPreference of(String kind, Long userId, boolean enabled, int leadDays) {
        NotificationPreference preference = new NotificationPreference();
        preference.kind = kind;
        preference.userId = userId;
        preference.enabled = enabled;
        preference.leadDays = leadDays;
        return preference;
    }

    public void update(Boolean enabled, Integer leadDays) {
        if (enabled != null) this.enabled = enabled;
        if (leadDays != null) this.leadDays = leadDays;
    }

    /** null 이면 그대로 둔다. 큰 것부터 중복 없이 저장한다 */
    public void updateLeadDaysList(List<Integer> leadDaysList) {
        if (leadDaysList != null) {
            this.leadDaysList = leadDaysList.stream().distinct().sorted(java.util.Comparator.reverseOrder()).toList();
        }
    }

    /** 이 시점(D-n)의 알림을 받는지. 고른 적 없으면 기본 시점 전부 */
    public boolean wantsLeadDay(int leadDays) {
        return NotificationLeadDays.effective(kind, leadDaysList).contains(leadDays);
    }

    /** null 이면 그대로 둔다 */
    public void updateEmailEnabled(Boolean emailEnabled) {
        if (emailEnabled != null) this.emailEnabled = emailEnabled;
    }
}
