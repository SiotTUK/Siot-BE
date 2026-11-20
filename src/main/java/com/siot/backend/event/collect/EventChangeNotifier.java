package com.siot.backend.event.collect;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 수집 중 일정이 바뀐 행사를 알림(NTF-02 일정 변경)으로 넘긴다 (EVE-01 6항).
 * 알림 발송은 역할 A 가 구현한다. 지금은 로그만 남기는 {@link LoggingEventChangeNotifier}.
 */
public interface EventChangeNotifier {

    void scheduleChanged(ScheduleChange change);

    /** 바뀌기 전·후 일정 */
    record ScheduleChange(Long eventId, LocalDate oldStartsOn, LocalDate oldEndsOn, LocalTime oldStartsAt,
                          LocalTime oldEndsAt, LocalDate newStartsOn, LocalDate newEndsOn, LocalTime newStartsAt,
                          LocalTime newEndsAt) {
    }
}
