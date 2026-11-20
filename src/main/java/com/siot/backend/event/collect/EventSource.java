package com.siot.backend.event.collect;

import java.time.LocalDate;

/** 행사 출처 하나. 실패하면 {@link EventSourceException} (접속 실패, 응답 형식 이상, 키 없음) */
public interface EventSource {

    EventSourceType type();

    /**
     * 모든 페이지를 받은 뒤 돌려준다 (중간에 실패하면 아무것도 저장하지 않게).
     *
     * @param today 한국 시간 오늘. 종료일이 이보다 앞선 행사는 빼고 돌려준다
     */
    FetchResult fetch(LocalDate today);
}
