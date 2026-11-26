package com.siot.backend.event;

import com.jayway.jsonpath.JsonPath;
import com.siot.backend.TestcontainersConfiguration;
import com.siot.backend.auth.service.SignupVerificationService;
import com.siot.backend.event.collect.CollectedEvent;
import com.siot.backend.event.domain.EventCategory;
import com.siot.backend.event.collect.EventChangeNotifier;
import com.siot.backend.event.collect.EventChangeNotifier.ScheduleChange;
import com.siot.backend.event.collect.EventCollectionService;
import com.siot.backend.event.collect.EventRegion;
import com.siot.backend.event.collect.EventSource;
import com.siot.backend.event.collect.FestivalStandardEventSource;
import com.siot.backend.event.collect.FetchResult;
import com.siot.backend.event.collect.GgcEventSource;
import com.siot.backend.event.collect.TourApiEventSource;
import com.siot.backend.rag.ai.AiUnavailableException;
import com.siot.backend.translation.Translator;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * EVE-03·04·05·07~10, ADM-05 행사 API 통합 테스트. 오늘 = 2026-11-20 (한국 시간).
 * 수집 출처는 실제 빈을 감싸 응답을 고정하고, 번역(Gemini)과 일정 변경 알림은 모킹한다.
 */
@SpringBootTest(properties = {
        "siot.scheduling.enabled=false",
        "siot.event.translation-pause=0s"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EventApiIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 20);
    private static final String PASSWORD = "abcd1234";

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-11-20T03:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    StringRedisTemplate redisTemplate;
    @Autowired
    EventCollectionService collectionService;

    @MockitoBean
    Translator translator;
    @MockitoBean
    EventChangeNotifier changeNotifier;
    @MockitoSpyBean
    GgcEventSource ggc;
    @MockitoSpyBean
    FestivalStandardEventSource festival;
    @MockitoSpyBean
    TourApiEventSource tourApi;

    private Cookie member;
    private long memberId;
    private Cookie admin;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("DELETE FROM event_collection_run");
        jdbc.update("DELETE FROM event");
        reset(translator, changeNotifier);
        when(translator.translateAll(anyString())).thenAnswer(invocation -> {
            String text = invocation.getArgument(0);
            return Map.of("en", "EN " + text, "zh", "ZH " + text, "vi", "VI " + text);
        });
        fetchReturns(ggc, List.of());
        fetchReturns(festival, List.of());
        fetchReturns(tourApi, List.of());

        String memberEmail = newEmail();
        member = signup(memberEmail);
        memberId = userId(memberEmail);
        String adminEmail = newEmail();
        signup(adminEmail);
        jdbc.update("UPDATE app_user SET role = 'ADMIN' WHERE email = ?", adminEmail);
        admin = login(adminEmail);
    }

    // ================================================================ EVE-10 목록

    @Test
    void listsUpcomingByStartDateAndPastByMostRecentEnd() throws Exception {
        long inProgress = event("GGC", "b", "진행 중 전시", "2026-11-10", "2026-11-30", EventRegion.SIHEUNG);
        long soon = event("GGC", "a", "곧 열리는 공연", "2026-11-25", "2026-11-25", EventRegion.SIHEUNG);
        long december = event("GGC", "c", "경기 축제", "2026-12-01", "2026-12-03", EventRegion.GYEONGGI);
        long endedRecently = event("GGC", "d", "막 끝난 행사", "2026-11-14", "2026-11-15", EventRegion.SIHEUNG);
        long endedLongAgo = event("GGC", "e", "오래전 행사", "2026-10-01", "2026-11-01", EventRegion.SIHEUNG);
        long deleted = event("GGC", "f", "지운 행사", "2026-11-22", "2026-11-22", EventRegion.SIHEUNG);
        jdbc.update("UPDATE event SET status = 'DELETED' WHERE event_id = ?", deleted);

        mockMvc.perform(get("/api/events").cookie(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.content[*].eventId").value(contains(ids(inProgress, soon, december))));
        mockMvc.perform(get("/api/events").param("past", "true").cookie(member))
                .andExpect(jsonPath("$.data.content[*].eventId").value(contains(ids(endedRecently, endedLongAgo))));
        mockMvc.perform(get("/api/events").param("region", "SIHEUNG").cookie(member))
                .andExpect(jsonPath("$.data.content[*].eventId").value(contains(ids(inProgress, soon))));
        // 기간과 하루라도 겹치는 행사
        mockMvc.perform(get("/api/events").param("from", "2026-11-26").param("to", "2026-11-30").cookie(member))
                .andExpect(jsonPath("$.data.content[*].eventId").value(contains(ids(inProgress))));
        mockMvc.perform(get("/api/events").param("region", "SEOUL").cookie(member))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/events")).andExpect(status().isUnauthorized());
    }

    /** EVE-10 카테고리: 수집할 때 출처 분류로 정하고, 목록·달력은 카테고리로 거르고, 관리자가 고치면 수집이 덮어쓰지 않는다 */
    @Test
    void categoryComesFromSourceFiltersListsAndSurvivesRecollectionWhenAdminEdits() throws Exception {
        CollectedEvent concert = new CollectedEvent("c1", "시흥 합창 공연", LocalDate.of(2026, 12, 5),
                LocalDate.of(2026, 12, 5), null, null, null, "https://ggc.test/c1", null, null, EventRegion.SIHEUNG,
                EventCategory.fromGgc("공연"));
        CollectedEvent exhibition = new CollectedEvent("x1", "시흥 사진 전시", LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 20), null, null, null, "https://ggc.test/x1", null, null, EventRegion.SIHEUNG,
                EventCategory.fromGgc("전시"));
        fetchReturns(ggc, List.of(concert, exhibition));
        collectionService.collectAll();
        long concertId = jdbc.queryForObject("SELECT event_id FROM event WHERE external_id = 'c1'", Long.class);
        long exhibitionId = jdbc.queryForObject("SELECT event_id FROM event WHERE external_id = 'x1'", Long.class);

        mockMvc.perform(get("/api/events").param("category", "PERFORMANCE").cookie(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].eventId").value(contains(ids(concertId))))
                .andExpect(jsonPath("$.data.content[0].category").value("PERFORMANCE"))
                .andExpect(jsonPath("$.data.content[0].categoryName").value("공연"));
        mockMvc.perform(get("/api/events").param("category", "exhibition").cookie(member)) // 대소문자 무시
                .andExpect(jsonPath("$.data.content[*].eventId").value(contains(ids(exhibitionId))));
        mockMvc.perform(get("/api/events/calendar").param("year", "2026").param("month", "12")
                        .param("category", "EXHIBITION").cookie(member))
                .andExpect(jsonPath("$.data[*].eventId").value(contains(ids(exhibitionId))));
        mockMvc.perform(get("/api/events/" + concertId).cookie(member))
                .andExpect(jsonPath("$.data.category").value("PERFORMANCE"));
        mockMvc.perform(get("/api/events").param("category", "SPORTS").cookie(member))
                .andExpect(status().isBadRequest());

        // 관리자가 카테고리를 고치면 잠근다 → 다시 수집해도 그대로. revision(번역)은 올리지 않는다
        mockMvc.perform(patch("/api/admin/events/" + concertId).with(csrf()).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"category\": \"FESTIVAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.category").value("FESTIVAL"))
                .andExpect(jsonPath("$.data.lockedFields").value(contains("category")))
                .andExpect(jsonPath("$.data.revision").value(1));
        collectionService.collectAll();
        mockMvc.perform(get("/api/admin/events").param("category", "FESTIVAL").cookie(admin))
                .andExpect(jsonPath("$.data.content[*].eventId").value(contains(ids(concertId))));

        // 관리자 등록: 비우면 EVENT, 한국어 강좌는 EDUCATION
        mockMvc.perform(post("/api/admin/events").with(csrf()).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"title": "외국인 한국어 교실", "startsOn": "2026-12-10", "region": "SIHEUNG",
                                 "category": "EDUCATION"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.category").value("EDUCATION"));
        mockMvc.perform(post("/api/admin/events").with(csrf()).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"title": "카테고리 잘못", "startsOn": "2026-12-11", "region": "SIHEUNG", "category": "SPORTS"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fallsBackToKoreanWhenTranslationIsMissing() throws Exception {
        long translated = event("GGC", "t", "시흥시 합창 페스티벌", "2026-11-25", "2026-11-25", EventRegion.SIHEUNG);
        translation(translated, "en", "Siheung Choir Festival", "Siheung Arts Center");
        long untranslated = event("GGC", "u", "갯골 음악회", "2026-11-26", "2026-11-26", EventRegion.SIHEUNG);
        jdbc.update("UPDATE app_user SET language = 'en' WHERE user_id = ?", memberId);

        mockMvc.perform(get("/api/events").cookie(member))
                .andExpect(jsonPath("$.data.content[0].title").value("Siheung Choir Festival"))
                .andExpect(jsonPath("$.data.content[0].venue").value("Siheung Arts Center"))
                .andExpect(jsonPath("$.data.content[0].language").value("en"))
                .andExpect(jsonPath("$.data.content[0].translated").value(true))
                .andExpect(jsonPath("$.data.content[1].title").value("갯골 음악회"))
                .andExpect(jsonPath("$.data.content[1].language").value("ko"))
                .andExpect(jsonPath("$.data.content[1].translated").value(false));
        mockMvc.perform(get("/api/events/" + untranslated).cookie(member))
                .andExpect(jsonPath("$.data.title").value("갯골 음악회"))
                .andExpect(jsonPath("$.data.translated").value(false));
    }

    // ================================================================ EVE-04 상세

    @Test
    void detailShowsNullsForMissingValuesSubscriptionAndHidesDeleted() throws Exception {
        long eventId = event("GGC", "x", "어린이 뮤지컬", "2026-12-18", "2026-12-18", EventRegion.SIHEUNG);
        jdbc.update("UPDATE event SET price_info = '전석 10,000원', source_url = 'https://ggc.test/x', "
                + "starts_at = '14:00', time_is_unknown = FALSE WHERE event_id = ?", eventId);

        mockMvc.perform(get("/api/events/" + eventId).cookie(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("어린이 뮤지컬"))
                .andExpect(jsonPath("$.data.startsOn").value("2026-12-18"))
                .andExpect(jsonPath("$.data.startsAt").value("14:00:00"))
                .andExpect(jsonPath("$.data.timeUnknown").value(false))
                .andExpect(jsonPath("$.data.priceInfo").value("전석 10,000원"))
                .andExpect(jsonPath("$.data.sourceUrl").value("https://ggc.test/x"))
                .andExpect(jsonPath("$.data.venue").value(nullValue()))
                .andExpect(jsonPath("$.data.description").value(nullValue()))
                .andExpect(jsonPath("$.data.applicationUrl").value(nullValue()))
                .andExpect(jsonPath("$.data.region").value("SIHEUNG"))
                .andExpect(jsonPath("$.data.subscribed").value(false));

        jdbc.update("INSERT INTO event_subscription (user_id, event_id, enabled) VALUES (?, ?, FALSE)", memberId,
                eventId);
        mockMvc.perform(get("/api/events/" + eventId).cookie(member))
                .andExpect(jsonPath("$.data.subscribed").value(false));
        jdbc.update("UPDATE event_subscription SET enabled = TRUE WHERE event_id = ?", eventId);
        mockMvc.perform(get("/api/events/" + eventId).cookie(member))
                .andExpect(jsonPath("$.data.subscribed").value(true));

        jdbc.update("UPDATE event SET status = 'DELETED' WHERE event_id = ?", eventId);
        mockMvc.perform(get("/api/events/" + eventId).cookie(member))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
        mockMvc.perform(get("/api/events/999999999").cookie(member)).andExpect(status().isNotFound());
    }

    // ================================================================ EVE-05 추천

    @Test
    void recommendsByOnboardingTagsThenMulticulturalThenSiheungThenStartDate() throws Exception {
        long kids = event("GGC", "k", "가족 인형극", "2026-12-10", "2026-12-10", EventRegion.GYEONGGI);
        tag(kids, "FAMILY_KIDS");
        long kidsNewcomer = event("GGC", "n", "새내기 가족 정착 안내", "2026-12-20", "2026-12-20", EventRegion.GYEONGGI);
        tag(kidsNewcomer, "FAMILY_KIDS");
        tag(kidsNewcomer, "NEWCOMER");
        long multicultural = event("GGC", "m", "세계인의 날", "2026-12-01", "2026-12-01", EventRegion.GYEONGGI);
        jdbc.update("UPDATE event SET is_multicultural = TRUE WHERE event_id = ?", multicultural);
        long siheung = event("GGC", "s", "시흥 음악회", "2026-11-21", "2026-11-21", EventRegion.SIHEUNG);
        long gyeonggi = event("GGC", "g", "경기 음악회", "2026-11-21", "2026-11-21", EventRegion.GYEONGGI);
        long ended = event("GGC", "p", "지난 가족 행사", "2026-11-01", "2026-11-02", EventRegion.SIHEUNG);
        tag(ended, "FAMILY_KIDS");

        // 온보딩 전: 다문화 → 시흥 → 시작일
        mockMvc.perform(get("/api/events/recommendations").param("size", "10").cookie(member))
                .andExpect(jsonPath("$.data[*].event.eventId")
                        .value(contains(ids(multicultural, siheung, gyeonggi, kids, kidsNewcomer))))
                .andExpect(jsonPath("$.data[0].reasons").value(contains("MULTICULTURAL")))
                .andExpect(jsonPath("$.data[1].reasons").value(contains("SIHEUNG")));

        jdbc.update("UPDATE app_user SET user_type = 'NEW' WHERE user_id = ?", memberId);
        jdbc.update("INSERT INTO member_residence (user_id, residence) VALUES (?, 'KIDS')", memberId);

        mockMvc.perform(get("/api/events/recommendations").param("size", "10").cookie(member))
                .andExpect(jsonPath("$.data[*].event.eventId")
                        .value(contains(ids(kidsNewcomer, kids, multicultural, siheung, gyeonggi))))
                .andExpect(jsonPath("$.data[0].reasons").value(contains("FAMILY_KIDS", "NEWCOMER")))
                .andExpect(jsonPath("$.data[1].reasons").value(contains("FAMILY_KIDS")))
                .andExpect(jsonPath("$.data[4].reasons").isEmpty());
        mockMvc.perform(get("/api/events/recommendations").cookie(member))
                .andExpect(jsonPath("$.data.length()").value(3));
    }

    // ================================================================ EVE-03 달력, EVE-10 갱신 시각

    @Test
    void calendarReturnsEventsTouchingTheMonth() throws Exception {
        long spanning = event("GGC", "1", "11월 말부터 12월 초", "2026-11-28", "2026-12-02", EventRegion.SIHEUNG);
        long lastDay = event("GGC", "2", "12월 마지막 날", "2026-12-31", "2026-12-31", EventRegion.GYEONGGI);
        event("GGC", "3", "11월에 끝남", "2026-11-29", "2026-11-30", EventRegion.SIHEUNG);
        event("GGC", "4", "1월 행사", "2027-01-01", "2027-01-01", EventRegion.SIHEUNG);
        long deleted = event("GGC", "5", "지운 12월 행사", "2026-12-05", "2026-12-05", EventRegion.SIHEUNG);
        jdbc.update("UPDATE event SET status = 'DELETED' WHERE event_id = ?", deleted);

        mockMvc.perform(get("/api/events/calendar").param("year", "2026").param("month", "12").cookie(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].eventId").value(contains(ids(spanning, lastDay))))
                .andExpect(jsonPath("$.data[0].startsOn").value("2026-11-28"))
                .andExpect(jsonPath("$.data[0].endsOn").value("2026-12-02"))
                .andExpect(jsonPath("$.data[1].region").value("GYEONGGI"));
        mockMvc.perform(get("/api/events/calendar").param("year", "2026").param("month", "13").cookie(member))
                .andExpect(status().isBadRequest());
    }

    @Test
    void collectionStatusShowsLastSuccessAndLastRunPerSource() throws Exception {
        jdbc.update("INSERT INTO event_collection_run (source, started_at, finished_at, status, fetched_count, "
                + "upserted_count) VALUES ('GGC', '2026-11-19T18:00:00Z', '2026-11-19T18:01:00Z', 'SUCCEEDED', 10, 2)");
        jdbc.update("INSERT INTO event_collection_run (source, started_at, finished_at, status, error) "
                + "VALUES ('GGC', '2026-11-20T18:00:00Z', '2026-11-20T18:00:05Z', 'FAILED', 'not JSON')");

        mockMvc.perform(get("/api/events/collection-status").cookie(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lastUpdatedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.sources[*].source").value(contains("GGC", "FESTIVAL_STD", "TOURAPI")))
                .andExpect(jsonPath("$.data.sources[0].lastSucceededAt").isNotEmpty())
                .andExpect(jsonPath("$.data.sources[0].lastRun.status").value("FAILED"))
                .andExpect(jsonPath("$.data.sources[0].lastRun.error").value("not JSON"))
                .andExpect(jsonPath("$.data.sources[1].lastRun").value(nullValue()));
    }

    // ================================================================ EVE-07 등록

    @Test
    void createRejectsDuplicateUnlessForcedAndTranslates() throws Exception {
        long collected = event("GGC", "gaetgol", "제21회 시흥갯골축제", "2026-12-18", "2026-12-20", EventRegion.SIHEUNG);
        String body = """
                {"title": "2026 시흥갯골축제", "venue": "시흥갯골생태공원", "startsOn": "2026-12-18",
                 "endsOn": "2026-12-20", "region": "SIHEUNG", "priceInfo": "무료"}
                """;

        mockMvc.perform(post("/api/admin/events").with(csrf()).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EVENT"))
                .andExpect(jsonPath("$.details.eventIds").value(contains(ids(collected))));

        mockMvc.perform(post("/api/admin/events").param("force", "true").with(csrf()).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.source").value("MANUAL"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.timeUnknown").value(true))
                .andExpect(jsonPath("$.data.lockedFields").isEmpty())
                .andExpect(jsonPath("$.data.translatedLanguages").value(contains("ko", "en", "zh", "vi")));

        // 다른 날이면 중복이 아니다. 번역이 실패해도 등록은 되고, 다음 주기에 다시 번역한다
        when(translator.translateAll(anyString())).thenThrow(new AiUnavailableException("429 quota"));
        MvcResult created = mockMvc.perform(post("/api/admin/events").with(csrf()).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"title": "외국인 주민 한국어 교실", "startsOn": "2026-12-05", "region": "SIHEUNG"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.endsOn").value("2026-12-05"))
                .andExpect(jsonPath("$.data.multicultural").value(true))
                .andExpect(jsonPath("$.data.tags").value(contains("KOREAN_CLASS")))
                .andExpect(jsonPath("$.data.translatedLanguages").value(contains("ko")))
                .andReturn();
        long eventId = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.data.eventId"))
                .longValue();
        mockMvc.perform(get("/api/events/" + eventId).cookie(member))
                .andExpect(jsonPath("$.data.title").value("외국인 주민 한국어 교실"));

        mockMvc.perform(post("/api/admin/events").with(csrf()).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"title": "날짜 없는 행사", "region": "SIHEUNG"}
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/events").with(csrf()).cookie(member)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    // ================================================================ EVE-08 수정

    @Test
    void updateLocksChangedFieldsBumpsRevisionNotifiesAndSurvivesRecollection() throws Exception {
        long eventId = event("GGC", "g1", "시흥시 합창 페스티벌", "2026-12-18", "2026-12-18", EventRegion.SIHEUNG);

        mockMvc.perform(patch("/api/admin/events/" + eventId).with(csrf()).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"title": "시흥시 합창 페스티벌 (장소 변경)", "startsOn": "2026-12-19", "endsOn": "2026-12-19",
                                 "region": "GYEONGGI", "venue": "시흥ABC행복학습타운"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revision").value(2))
                .andExpect(jsonPath("$.data.title").value("시흥시 합창 페스티벌 (장소 변경)"))
                .andExpect(jsonPath("$.data.lockedFields")
                        .value(containsInAnyOrder("title", "venue", "startsOn", "endsOn", "region")))
                .andExpect(jsonPath("$.data.translatedLanguages").value(contains("ko")));
        verify(changeNotifier).scheduleChanged(new ScheduleChange(eventId, LocalDate.of(2026, 12, 18),
                LocalDate.of(2026, 12, 18), null, null, LocalDate.of(2026, 12, 19), LocalDate.of(2026, 12, 19),
                null, null));

        // 다시 수집해도 잠긴 칸은 그대로, 잠기지 않은 칸(가격)만 바뀐다
        fetchReturns(ggc, List.of(new CollectedEvent("g1", "시흥시 합창 페스티벌", LocalDate.of(2026, 12, 18),
                LocalDate.of(2026, 12, 18), null, null, "무료", "https://ggc.test/g1", null, null,
                EventRegion.SIHEUNG)));
        collectionService.collectAll();

        mockMvc.perform(get("/api/admin/events/" + eventId).cookie(admin))
                .andExpect(jsonPath("$.data.title").value("시흥시 합창 페스티벌 (장소 변경)"))
                .andExpect(jsonPath("$.data.venue").value("시흥ABC행복학습타운"))
                .andExpect(jsonPath("$.data.startsOn").value("2026-12-19"))
                .andExpect(jsonPath("$.data.region").value("GYEONGGI"))
                .andExpect(jsonPath("$.data.priceInfo").value("무료"))
                .andExpect(jsonPath("$.data.revision").value(3))
                // 수집 주기 끝에 바뀐 원문으로 다시 번역했다
                .andExpect(jsonPath("$.data.translatedLanguages").value(contains("ko", "en", "zh", "vi")));

        // 같은 값을 다시 보내면 revision 은 그대로
        mockMvc.perform(patch("/api/admin/events/" + eventId).with(csrf()).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"region\": \"GYEONGGI\"}"))
                .andExpect(jsonPath("$.data.revision").value(3));
        mockMvc.perform(patch("/api/admin/events/" + eventId).with(csrf()).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"startsOn\": \"2027-01-01\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/admin/events/999999999").with(csrf()).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"region\": \"GYEONGGI\"}"))
                .andExpect(status().isNotFound());
    }

    // ================================================================ EVE-09 삭제

    @Test
    void deletedEventStaysDeletedAfterRecollection() throws Exception {
        CollectedEvent collected = new CollectedEvent("del", "취소된 공연", LocalDate.of(2026, 12, 18),
                LocalDate.of(2026, 12, 18), null, null, "무료", "https://ggc.test/del", null, null,
                EventRegion.SIHEUNG);
        fetchReturns(ggc, List.of(collected));
        collectionService.collectAll();
        long eventId = jdbc.queryForObject("SELECT event_id FROM event WHERE external_id = 'del'", Long.class);

        mockMvc.perform(delete("/api/admin/events/" + eventId).with(csrf()).cookie(admin))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/events/" + eventId).cookie(member)).andExpect(status().isNotFound());

        collectionService.collectAll();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM event", Integer.class)).isEqualTo(1);
        mockMvc.perform(get("/api/admin/events").param("status", "DELETED").cookie(admin))
                .andExpect(jsonPath("$.data.content[*].eventId").value(contains(ids(eventId))));
        mockMvc.perform(get("/api/admin/events").param("title", "취소된").cookie(admin))
                .andExpect(jsonPath("$.data.content[0].status").value("DELETED"));
        mockMvc.perform(get("/api/events").cookie(member))
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(delete("/api/admin/events/999999999").with(csrf()).cookie(admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminListFiltersBySourceAndEscapesLikeCharacters() throws Exception {
        event("GGC", "l1", "100% 즐기는 축제", "2026-12-18", "2026-12-18", EventRegion.SIHEUNG);
        event("TOURAPI", "l2", "1000 즐기는 축제", "2026-12-18", "2026-12-18", EventRegion.SIHEUNG);

        mockMvc.perform(get("/api/admin/events").param("title", "100%").cookie(admin))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("100% 즐기는 축제"));
        mockMvc.perform(get("/api/admin/events").param("source", "TOURAPI").cookie(admin))
                .andExpect(jsonPath("$.data.content[*].source").value(contains("TOURAPI")));
    }

    // ================================================================ 수동 수집

    @Test
    void manualCollectionReturnsRunsAndRejectsWhileRunning() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            assertThat(release.await(30, TimeUnit.SECONDS)).isTrue();
            return new FetchResult(1, List.of(new CollectedEvent("m1", "수동 수집 행사", LocalDate.of(2026, 12, 18),
                    LocalDate.of(2026, 12, 18), null, null, null, null, null, null, EventRegion.SIHEUNG)));
        }).when(ggc).fetch(any());

        CompletableFuture<MvcResult> first = CompletableFuture.supplyAsync(() -> {
            try {
                return mockMvc.perform(post("/api/admin/events/collect").with(csrf()).cookie(admin)).andReturn();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        try {
            assertThat(entered.await(30, TimeUnit.SECONDS)).isTrue();
            mockMvc.perform(post("/api/admin/events/collect").with(csrf()).cookie(admin))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("EVENT_COLLECTION_RUNNING"));
        } finally {
            release.countDown();
        }

        MvcResult result = first.get(60, TimeUnit.SECONDS);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String json = result.getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(json, "$.data[*].source"))
                .containsExactly("GGC", "FESTIVAL_STD", "TOURAPI");
        assertThat(JsonPath.<List<String>>read(json, "$.data[*].status")).containsOnly("SUCCEEDED");
        assertThat(JsonPath.<Integer>read(json, "$.data[0].upsertedCount")).isEqualTo(1);
        awaitIdle();
        // 번역은 뒤에서 했다
        assertThat(jdbc.queryForObject("SELECT count(*) FROM event_translation t JOIN event e USING (event_id) "
                + "WHERE e.external_id = 'm1' AND t.language IN ('en', 'zh', 'vi')", Integer.class)).isEqualTo(3);

        // 끝나면 다시 실행할 수 있다
        mockMvc.perform(post("/api/admin/events/collect").with(csrf()).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));
        awaitIdle();
        verify(changeNotifier, never()).scheduleChanged(any());
    }

    // ================================================================ 도우미

    private void awaitIdle() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (collectionService.isRunning() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertThat(collectionService.isRunning()).isFalse();
    }

    private static void fetchReturns(EventSource source, List<CollectedEvent> events) {
        doReturn(new FetchResult(events.size(), events)).when(source).fetch(any());
    }

    /** 원문(ko) 행까지 있는 ACTIVE 행사 */
    private long event(String source, String externalId, String title, String startsOn, String endsOn,
                       String region) {
        Long eventId = jdbc.queryForObject("INSERT INTO event (external_source, external_id, region_code, starts_on, "
                        + "ends_on, time_is_unknown) VALUES (?, ?, ?, ?::date, ?::date, TRUE) RETURNING event_id",
                Long.class, source, externalId, region, startsOn, endsOn);
        jdbc.update("INSERT INTO event_translation (language, event_id, title, source_revision) VALUES ('ko', ?, ?, 1)",
                eventId, title);
        return eventId;
    }

    private void translation(long eventId, String language, String title, String venue) {
        jdbc.update("INSERT INTO event_translation (language, event_id, title, venue, source_revision) "
                + "VALUES (?, ?, ?, ?, 1)", language, eventId, title, venue);
    }

    private void tag(long eventId, String tag) {
        jdbc.update("INSERT INTO event_tag (event_id, tag) VALUES (?, ?)", eventId, tag);
    }

    /** JSON 숫자 비교용 (jsonPath 는 작은 수를 Integer 로 읽는다) */
    private static Object[] ids(long... ids) {
        Object[] values = new Object[ids.length];
        for (int i = 0; i < ids.length; i++) {
            values[i] = ids[i] <= Integer.MAX_VALUE ? (Object) (int) ids[i] : (Object) ids[i];
        }
        return values;
    }

    private long userId(String email) {
        return jdbc.queryForObject("SELECT user_id FROM app_user WHERE email = ?", Long.class, email);
    }

    private Cookie signup(String email) throws Exception {
        redisTemplate.opsForValue().set(SignupVerificationService.VERIFIED_KEY + email, "1");
        MvcResult result = mockMvc.perform(post("/api/auth/signup").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                                + "\",\"passwordConfirm\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return result.getResponse().getCookie("SIOT_SESSION");
    }

    private Cookie login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie("SIOT_SESSION");
    }

    private static String newEmail() {
        return "ev" + ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L) + "@example.com";
    }
}
