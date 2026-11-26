package com.siot.backend.event.collect;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 일정 변경 로그만 남긴다.
 * TODO(역할 A, NTF-02): 이 행사를 구독한 사용자에게 일정 변경 알림을 만드는 구현으로 바꾼다 (이 클래스를 지우고 새 구현을 빈으로 등록).
 */
@Slf4j
@Component
public class LoggingEventChangeNotifier implements EventChangeNotifier {

    @Override
    public void scheduleChanged(ScheduleChange change) {
        log.info("Event schedule changed (NTF-02 not implemented yet): {}", change);
    }
}
