package com.siot.backend.event.collect;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 일정이 바뀐 행사를 알림(NTF-02 일정 변경)으로 넘긴다. 수집(EVE-01 6항)과 관리자 수정(EVE-08)이 커밋 뒤 부른다.
 * 구현: notification 모듈의 NotificationEventChangeNotifier (신청자에게 즉시 변경 알림).
 */
public interface EventChangeNotifier {

    void scheduleChanged(ScheduleChange change);

    /** 바뀌기 전·후 일정 */
    record ScheduleChange(Long eventId, LocalDate oldStartsOn, LocalDate oldEndsOn, LocalTime oldStartsAt,
                          LocalTime oldEndsAt, LocalDate newStartsOn, LocalDate newEndsOn, LocalTime newStartsAt,
                          LocalTime newEndsAt) {
    }
}
