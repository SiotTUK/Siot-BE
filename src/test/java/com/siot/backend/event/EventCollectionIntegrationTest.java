package com.siot.backend.event;

import com.siot.backend.TestcontainersConfiguration;
import com.siot.backend.event.collect.CollectedEvent;
import com.siot.backend.event.collect.EventChangeNotifier;
import com.siot.backend.event.collect.EventChangeNotifier.ScheduleChange;
import com.siot.backend.event.collect.EventCollectionRunRecorder;
import com.siot.backend.event.collect.EventCollectionService;
import com.siot.backend.event.collect.EventRegion;
import com.siot.backend.event.collect.EventSource;
import com.siot.backend.event.collect.EventSourceException;
import com.siot.backend.event.collect.EventSourceType;
import com.siot.backend.event.collect.EventTranslationUpdater;
import com.siot.backend.event.collect.EventUpsertService;
import com.siot.backend.event.collect.FetchResult;
import com.siot.backend.event.domain.Event;
import com.siot.backend.event.domain.EventCollectionRun;
import com.siot.backend.event.domain.EventTag;
import com.siot.backend.event.domain.EventTranslation;
import com.siot.backend.event.repository.EventCollectionRunRepository;
import com.siot.backend.event.repository.EventRepository;
import com.siot.backend.event.repository.EventTagRepository;
import com.siot.backend.event.repository.EventTranslationRepository;
import com.siot.backend.rag.ai.AiUnavailableException;
import com.siot.backend.translation.Translator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EVE-01·EVE-02 행사 수집 통합 테스트. 출처는 고정 응답, 번역(Gemini)과 일정 변경 알림은 모킹한다.
 */
@SpringBootTest(properties = {
        "siot.scheduling.enabled=false",
        "siot.event.translation-pause=0s"
})
@Import(TestcontainersConfiguration.class)
class EventCollectionIntegrationTest {

    /** 오늘 = 2026-11-20 (한국 시간) */
    private static final Clock CLOCK = Clock.fixed(java.time.Instant.parse("2026-11-20T00:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDate DAY = LocalDate.of(2026, 12, 18);

    @Autowired
    private EventUpsertService upsertService;
    @Autowired
    private EventCollectionRunRecorder recorder;
    @Autowired
    private EventTranslationUpdater translationUpdater;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private EventTranslationRepository translationRepository;
    @Autowired
    private EventTagRepository tagRepository;
    @Autowired
    private EventCollectionRunRepository runRepository;
    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private Translator translator;
    @MockitoBean
    private EventChangeNotifier changeNotifier;

    /** 출처별 고정 응답 (없으면 빈 응답, 예외면 실패) */
    private final Map<EventSourceType, Object> responses = new EnumMap<>(EventSourceType.class);

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM event_collection_run");
        jdbc.update("DELETE FROM event");
        responses.clear();
        reset(translator, changeNotifier);
        when(translator.translateAll(anyString())).thenAnswer(invocation -> {
            String text = invocation.getArgument(0);
            return Map.of("en", "EN " + text, "zh", "ZH " + text, "vi", "VI " + text);
        });
    }

    private EventCollectionService service() {
        List<EventSource> sources = new ArrayList<>();
        for (EventSourceType type : EventSourceType.values()) {
            sources.add(new EventSource() {
                @Override
                public EventSourceType type() {
                    return type;
                }

                @Override
                @SuppressWarnings("unchecked")
                public FetchResult fetch(LocalDate today) {
                    Object response = responses.get(type);
                    if (response instanceof RuntimeException e) {
                        throw e;
                    }
                    List<CollectedEvent> events = response == null ? List.of() : (List<CollectedEvent>) response;
                    return new FetchResult(events.size(), events);
                }
            });
        }
        return new EventCollectionService(sources, upsertService, recorder, changeNotifier, translationUpdater, CLOCK);
    }

    private static CollectedEvent ggc(String id, String title, LocalDate start, LocalDate end, LocalTime startsAt,
                                      String price) {
        return new CollectedEvent(id, title, start, end, startsAt, null, price, "https://example.test/" + id,
                null, null, EventRegion.SIHEUNG);
    }

    private Event event(EventSourceType source, String id) {
        return eventRepository.findByExternalSourceAndExternalId(source.name(), id).orElseThrow();
    }

    private EventTranslation translation(Long eventId, String language) {
        return translationRepository.findByEventId(eventId).stream()
                .filter(row -> row.getLanguage().equals(language)).findFirst().orElse(null);
    }

    private EventCollectionRun lastRun(EventSourceType source) {
        return runRepository.findBySourceOrderByIdDesc(source.name()).getFirst();
    }

    @Test
    void collectsEventsWithOriginalTranslationsTagsAndRunRecords() {
        responses.put(EventSourceType.GGC, List.of(
                ggc("g1", "어린이 뮤지컬 <구름빵>", DAY, DAY, LocalTime.of(14, 0), "전석 10,000원"),
                ggc("g2", "다문화가족 한마당", DAY, DAY.plusDays(1), null, "무료")));

        service().collectAll();

        Event kids = event(EventSourceType.GGC, "g1");
        assertThat(kids.getRevision()).isEqualTo(1);
        assertThat(kids.getStartsAt()).isEqualTo(LocalTime.of(14, 0));
        assertThat(kids.isTimeUnknown()).isFalse();
        assertThat(kids.getPriceInfo()).isEqualTo("전석 10,000원");
        assertThat(kids.getRegionCode()).isEqualTo(EventRegion.SIHEUNG);
        assertThat(tagRepository.findByEventId(kids.getId())).extracting(EventTag::getTag)
                .containsExactly(EventTag.FAMILY_KIDS);
        assertThat(translation(kids.getId(), "ko").getTitle()).isEqualTo("어린이 뮤지컬 <구름빵>");
        assertThat(translation(kids.getId(), "ko").getSourceRevision()).isEqualTo(1);
        assertThat(translation(kids.getId(), "en").getTitle()).isEqualTo("EN 어린이 뮤지컬 <구름빵>");
        assertThat(translation(kids.getId(), "vi").getSourceRevision()).isEqualTo(1);

        Event multicultural = event(EventSourceType.GGC, "g2");
        assertThat(multicultural.isMulticultural()).isTrue();
        assertThat(multicultural.isTimeUnknown()).isTrue();

        assertThat(lastRun(EventSourceType.GGC).getStatus()).isEqualTo(EventCollectionRun.SUCCEEDED);
        assertThat(lastRun(EventSourceType.GGC).getUpsertedCount()).isEqualTo(2);
        assertThat(lastRun(EventSourceType.TOURAPI).getStatus()).isEqualTo(EventCollectionRun.SUCCEEDED);
        assertThat(lastRun(EventSourceType.FESTIVAL_STD).getFinishedAt()).isNotNull();
    }

    @Test
    void recollectKeepsIdAndRevisionUnlessChangedThenNotifiesScheduleChange() {
        responses.put(EventSourceType.GGC, List.of(ggc("g1", "시흥시 합창 페스티벌", DAY, DAY, LocalTime.of(19, 30), "무료")));
        service().collectAll();
        Long eventId = event(EventSourceType.GGC, "g1").getId();

        service().collectAll();
        assertThat(event(EventSourceType.GGC, "g1").getRevision()).isEqualTo(1);
        assertThat(lastRun(EventSourceType.GGC).getUpsertedCount()).isZero();
        verify(changeNotifier, never()).scheduleChanged(any());

        responses.put(EventSourceType.GGC, List.of(ggc("g1", "시흥시 합창 페스티벌", DAY.plusDays(7), DAY.plusDays(7),
                LocalTime.of(19, 30), "무료")));
        service().collectAll();

        Event changed = event(EventSourceType.GGC, "g1");
        assertThat(changed.getId()).isEqualTo(eventId);
        assertThat(changed.getRevision()).isEqualTo(2);
        assertThat(changed.getStartsOn()).isEqualTo(DAY.plusDays(7));
        verify(changeNotifier).scheduleChanged(new ScheduleChange(eventId, DAY, DAY, LocalTime.of(19, 30), null,
                DAY.plusDays(7), DAY.plusDays(7), LocalTime.of(19, 30), null));
        // 원문이 바뀌었으니 번역을 다시 만든다
        assertThat(translation(eventId, "ko").getSourceRevision()).isEqualTo(2);
        assertThat(translation(eventId, "zh").getSourceRevision()).isEqualTo(2);
    }

    @Test
    void keepsLockedFieldsAndDeletedEvents() {
        responses.put(EventSourceType.GGC, List.of(
                ggc("locked", "관리자가 고친 공연", DAY, DAY, null, "무료"),
                ggc("deleted", "관리자가 지운 공연", DAY, DAY, null, "무료")));
        service().collectAll();
        Long lockedId = event(EventSourceType.GGC, "locked").getId();
        Long deletedId = event(EventSourceType.GGC, "deleted").getId();
        jdbc.update("UPDATE event SET starts_on = ?, starts_on_locked = TRUE, price_info = '관리자 가격', "
                + "price_info_locked = TRUE WHERE event_id = ?", DAY.minusDays(1), lockedId);
        jdbc.update("UPDATE event_translation SET title = '관리자 제목', title_locked = TRUE "
                + "WHERE event_id = ? AND language = 'ko'", lockedId);
        jdbc.update("UPDATE event SET status = 'DELETED' WHERE event_id = ?", deletedId);

        responses.put(EventSourceType.GGC, List.of(
                ggc("locked", "수집된 새 제목", DAY.plusDays(3), DAY.plusDays(5), null, "전석 5,000원"),
                ggc("deleted", "관리자가 지운 공연 (재공연)", DAY.plusDays(3), DAY.plusDays(3), null, "무료")));
        service().collectAll();

        Event locked = event(EventSourceType.GGC, "locked");
        assertThat(locked.getStartsOn()).isEqualTo(DAY.minusDays(1));
        assertThat(locked.getEndsOn()).isEqualTo(DAY.plusDays(5));   // 잠기지 않은 칸은 갱신
        assertThat(locked.getPriceInfo()).isEqualTo("관리자 가격");
        assertThat(translation(lockedId, "ko").getTitle()).isEqualTo("관리자 제목");

        Event deleted = event(EventSourceType.GGC, "deleted");
        assertThat(deleted.getStatus()).isEqualTo(Event.STATUS_DELETED);
        assertThat(deleted.getRevision()).isEqualTo(1);
        assertThat(deleted.getStartsOn()).isEqualTo(DAY);
        assertThat(translation(deletedId, "ko").getTitle()).isEqualTo("관리자가 지운 공연");
    }

    @Test
    void failedSourceKeepsExistingEventsAndOthersStillRun() {
        responses.put(EventSourceType.GGC, List.of(ggc("g1", "시흥시 합창 페스티벌", DAY, DAY, null, "무료")));
        service().collectAll();

        responses.put(EventSourceType.GGC, new EventSourceException("GGC page 0: response is not JSON"));
        responses.put(EventSourceType.TOURAPI, List.of(new CollectedEvent("t1", "MOIM International", DAY, DAY,
                null, null, null, null, "경기도 시흥시 서해안로 2007", null, EventRegion.SIHEUNG)));
        service().collectAll();

        EventCollectionRun failed = lastRun(EventSourceType.GGC);
        assertThat(failed.getStatus()).isEqualTo(EventCollectionRun.FAILED);
        assertThat(failed.getError()).contains("not JSON");
        assertThat(eventRepository.findByExternalSourceAndExternalId("GGC", "g1")).isPresent();
        assertThat(lastRun(EventSourceType.TOURAPI).getStatus()).isEqualTo(EventCollectionRun.SUCCEEDED);
        assertThat(eventRepository.findByExternalSourceAndExternalId("TOURAPI", "t1")).isPresent();
    }

    /** 먼저 저장된 지지씨 행사를 남기고, 축제표준데이터는 빈 칸(장소·설명)만 채운다 */
    @Test
    void duplicateFromAnotherSourceOnlyFillsBlanks() {
        responses.put(EventSourceType.GGC, List.of(ggc("g1", "제21회 시흥갯골축제", DAY, DAY.plusDays(2), null, "무료")));
        responses.put(EventSourceType.FESTIVAL_STD, List.of(new CollectedEvent("시흥갯골축제|" + DAY, "시흥갯골축제",
                DAY, DAY.plusDays(2), null, null, null, null, "시흥갯골생태공원", "갯골에서 열리는 축제", EventRegion.SIHEUNG)));
        responses.put(EventSourceType.TOURAPI, List.of(new CollectedEvent("t1", "2026 시흥갯골축제", DAY,
                DAY.plusDays(2), null, null, null, null, "경기도 시흥시 동서로 287", null, EventRegion.SIHEUNG)));

        service().collectAll();

        assertThat(eventRepository.count()).isEqualTo(1);
        Event kept = event(EventSourceType.GGC, "g1");
        EventTranslation original = translation(kept.getId(), "ko");
        assertThat(original.getTitle()).isEqualTo("제21회 시흥갯골축제");
        assertThat(original.getVenue()).isEqualTo("시흥갯골생태공원");   // TourAPI 주소로 덮지 않는다
        assertThat(original.getDescription()).isEqualTo("갯골에서 열리는 축제");
        assertThat(kept.getRevision()).isEqualTo(2);
        assertThat(translation(kept.getId(), "en").getVenue()).isEqualTo("EN 시흥갯골생태공원");
    }

    @Test
    void failedTranslationIsRetriedNextCycle() {
        when(translator.translateAll(anyString())).thenThrow(new AiUnavailableException("429 quota"));
        responses.put(EventSourceType.GGC, List.of(ggc("g1", "시흥시 합창 페스티벌", DAY, DAY, null, "무료")));

        service().collectAll();

        Long eventId = event(EventSourceType.GGC, "g1").getId();
        assertThat(translation(eventId, "en")).isNull();
        assertThat(lastRun(EventSourceType.GGC).getStatus()).isEqualTo(EventCollectionRun.SUCCEEDED);

        reset(translator);
        when(translator.translateAll(anyString())).thenReturn(Map.of("en", "Siheung Choir Festival",
                "zh", "始兴合唱节", "vi", "Lễ hội hợp xướng Siheung"));
        service().collectAll();

        assertThat(translation(eventId, "en").getTitle()).isEqualTo("Siheung Choir Festival");
        assertThat(translation(eventId, "en").getSourceRevision()).isEqualTo(1);
    }
}
