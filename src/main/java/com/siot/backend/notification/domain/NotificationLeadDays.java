package com.siot.backend.notification.domain;

import java.util.List;
import java.util.Map;

/**
 * NTF-03 알림 종류별로 고를 수 있는 사전 알림 시점 (D-n). 회원이 고르지 않으면 전부 보낸다 (기본).
 * 행사(EVENT)는 신청마다 시점을 고르고(event_subscription.lead_days), 안내 변경(NOTICE_CHANGED)은 시점이 없다.
 */
public final class NotificationLeadDays {

    private static final Map<String, List<Integer>> AVAILABLE = Map.of(
            "STAY_EXPIRY", List.of(120, 60, 30, 14, 7, 1),
            "CHECKLIST_DEADLINE", List.of(7, 1),
            "TAX", List.of(7, 1));

    private NotificationLeadDays() {
    }

    /** 그 종류에서 고를 수 있는 시점 (큰 것부터). 시점이 없는 종류는 빈 목록 */
    public static List<Integer> available(String kind) {
        return AVAILABLE.getOrDefault(kind, List.of());
    }

    /** 회원이 고른 시점 (없으면 기본 = 전부). 고를 수 없는 값은 뺀다 */
    public static List<Integer> effective(String kind, List<Integer> chosen) {
        List<Integer> available = available(kind);
        if (chosen == null || chosen.isEmpty()) {
            return available;
        }
        return available.stream().filter(chosen::contains).toList();
    }
}
