package com.siot.backend.schedule.holiday;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Duration;

/** 공휴일 조회와 지방세 기한 계산 */
@Configuration
@EnableConfigurationProperties(HolidayProperties.class)
public class HolidayConfig {

    @Bean
    public HolidaySource holidaySource(HolidayProperties props, JsonMapper jsonMapper) {
        Duration timeout = props.httpTimeout() != null ? props.httpTimeout() : Duration.ofSeconds(10);
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(timeout);
        // 다른 RestClient 빈과 섞이지 않도록 빈으로 내놓지 않는다
        RestClient restClient = RestClient.builder().requestFactory(factory).build();
        return new KasiHolidaySource(restClient, props, jsonMapper);
    }

    @Bean
    public TaxDeadlineCalendar taxDeadlineCalendar(HolidaySource holidaySource) {
        return new TaxDeadlineCalendar(holidaySource);
    }
}
