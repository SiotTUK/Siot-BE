package com.siot.backend.schedule.holiday;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 공휴일 조회 (한국천문연구원 특일 정보, 공공데이터포털).
 *
 * @param baseUrl     getRestDeInfo 주소
 * @param apiKey      공공데이터포털 인증키 (특일 정보 활용신청 필요). 비어 있으면 고정 공휴일·주말만으로 계산한다
 * @param httpTimeout 연결·응답 대기 시간
 */
@ConfigurationProperties(prefix = "siot.holiday")
public record HolidayProperties(String baseUrl, String apiKey, Duration httpTimeout) {

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
