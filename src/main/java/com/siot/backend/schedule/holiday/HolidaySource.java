package com.siot.backend.schedule.holiday;

import java.time.LocalDate;
import java.util.Set;

/** 한 해의 공휴일 (대체공휴일·임시공휴일 포함) */
@FunctionalInterface
public interface HolidaySource {

    /**
     * @return 그해 공휴일. 아직 발표되지 않았거나 확인할 수 없으면 {@link HolidayUnavailableException}
     */
    Set<LocalDate> holidays(int year);

    class HolidayUnavailableException extends RuntimeException {
        public HolidayUnavailableException(String message) {
            super(message);
        }

        public HolidayUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
