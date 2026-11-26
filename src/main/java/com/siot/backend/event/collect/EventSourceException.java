package com.siot.backend.event.collect;

/** 출처에서 행사를 받지 못함. 기존 행사는 그대로 두고 event_collection_run 에 FAILED 로 남긴다 */
public class EventSourceException extends RuntimeException {

    public EventSourceException(String message) {
        super(message);
    }

    public EventSourceException(String message, Throwable cause) {
        super(message, cause);
    }
}
