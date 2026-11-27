package com.siot.backend.event.collect;

import com.siot.backend.event.repository.EventRepository;
import com.siot.backend.event.repository.EventTranslationRepository;
import com.siot.backend.translation.Translator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Clock;
import java.util.List;

/** 행사 수집 (EVE-01·EVE-02) 연결: 출처 3개, 번역, 수집 서비스 */
@Configuration
@EnableConfigurationProperties({EventProperties.class, EventVisibilityProperties.class})
public class EventCollectConfig {

    /** 외부 행사 API 전용 클라이언트 (연결·응답 대기 시간 = siot.event.http-timeout) */
    @Bean
    public RestClient eventRestClient(EventProperties props) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(props.httpTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(props.httpTimeout());
        return RestClient.builder().requestFactory(factory).build();
    }

    @Bean
    public GgcEventSource ggcEventSource(RestClient eventRestClient, EventProperties props, JsonMapper jsonMapper) {
        return new GgcEventSource(eventRestClient, props.ggc(), jsonMapper);
    }

    @Bean
    public FestivalStandardEventSource festivalStandardEventSource(RestClient eventRestClient, EventProperties props,
                                                                  JsonMapper jsonMapper) {
        return new FestivalStandardEventSource(eventRestClient, props, jsonMapper);
    }

    @Bean
    public TourApiEventSource tourApiEventSource(RestClient eventRestClient, EventProperties props,
                                                 JsonMapper jsonMapper) {
        return new TourApiEventSource(eventRestClient, props, jsonMapper);
    }

    @Bean
    public EventTranslationUpdater eventTranslationUpdater(EventRepository eventRepository,
                                                           EventTranslationRepository translationRepository,
                                                           Translator translator,
                                                           PlatformTransactionManager transactionManager,
                                                           EventProperties props) {
        return new EventTranslationUpdater(eventRepository, translationRepository, translator,
                new TransactionTemplate(transactionManager), props.translationPause(), Thread::sleep);
    }

    @Bean
    public EventCollectionService eventCollectionService(List<EventSource> sources, EventUpsertService upsertService,
                                                         EventCollectionRunRecorder recorder,
                                                         EventChangeNotifier changeNotifier,
                                                         EventTranslationUpdater translationUpdater, Clock clock) {
        return new EventCollectionService(sources, upsertService, recorder, changeNotifier, translationUpdater, clock);
    }
}
