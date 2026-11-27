package com.siot.backend.event.service;

import com.siot.backend.event.collect.EventRegion;
import com.siot.backend.event.collect.EventSourceType;
import com.siot.backend.event.domain.Event;
import com.siot.backend.event.domain.EventCategory;
import com.siot.backend.event.domain.EventCollectionRun;
import com.siot.backend.event.domain.EventTag;
import com.siot.backend.event.dto.EventCalendarItemResponse;
import com.siot.backend.event.dto.EventCollectionRunResponse;
import com.siot.backend.event.dto.EventCollectionStatusResponse;
import com.siot.backend.event.dto.EventDetailResponse;
import com.siot.backend.event.dto.EventRecommendationResponse;
import com.siot.backend.event.dto.EventSummaryResponse;
import com.siot.backend.event.repository.EventCollectionRunRepository;
import com.siot.backend.event.repository.EventRepository;
import com.siot.backend.event.repository.EventSubscriptionRepository;
import com.siot.backend.event.repository.EventTagRepository;
import com.siot.backend.global.common.PageResponse;
import com.siot.backend.global.common.SupportedLanguages;
import com.siot.backend.global.exception.BusinessException;
import com.siot.backend.global.exception.ErrorCode;
import com.siot.backend.member.domain.AppUser;
import com.siot.backend.member.domain.Residence;
import com.siot.backend.member.domain.UserType;
import com.siot.backend.member.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 회원 행사 조회 (EVE-03·04·05·10). 보이는 행사 = status ACTIVE. 날짜는 한국 시간 오늘 기준.
 * 종료일이 없으면 시작일, 시작일이 없으면 종료일로 본다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventQueryService {

    /** 추천 이유 (태그 외) */
    public static final String REASON_MULTICULTURAL = "MULTICULTURAL";
    public static final String REASON_SIHEUNG = "SIHEUNG";

    static final Set<String> REGIONS = Set.of(EventRegion.SIHEUNG, EventRegion.GYEONGGI);
    private static final LocalDate MIN_DATE = LocalDate.of(1900, 1, 1);
    private static final LocalDate MAX_DATE = LocalDate.of(9999, 12, 31);
    /** 태그 순서 (추천 이유 순서) */
    private static final List<String> TAG_ORDER = List.of(EventTag.FAMILY_KIDS, EventTag.KOREAN_CLASS,
            EventTag.NEWCOMER);

    private final EventRepository eventRepository;
    private final EventTagRepository tagRepository;
    private final EventSubscriptionRepository subscriptionRepository;
    private final EventCollectionRunRepository runRepository;
    private final AppUserRepository userRepository;
    private final EventLocalizer localizer;
    private final Clock clock;

    /**
     * EVE-10 목록. 기본은 진행 예정·진행 중(시작일 가까운 순), past=true 면 지난 행사(최근에 끝난 순).
     *
     * @param region SIHEUNG / GYEONGGI, null 이면 전체
     * @param from   이 날 이후에 끝나는 행사 (null 이면 제한 없음)
     * @param to     이 날 이전에 시작하는 행사 (null 이면 제한 없음)
     */
    public PageResponse<EventSummaryResponse> list(Long userId, String region, LocalDate from, LocalDate to,
                                                   boolean past, int page, int size) {
        return list(userId, region, null, from, to, past, page, size);
    }

    /** EVE-10 목록 정렬: DATE(기본, 날짜순) / DEADLINE(신청 마감 임박 순) / LATEST(최근 등록 순) */
    public static final List<String> SORTS = List.of("DATE", "DEADLINE", "LATEST");

    /**
     * @param category {@link EventCategory} 코드, null 이면 전체 (EVE-10 카테고리 필터)
     */
    public PageResponse<EventSummaryResponse> list(Long userId, String region, String category, LocalDate from,
                                                   LocalDate to, boolean past, int page, int size) {
        return list(userId, region, category, from, to, past, null, page, size);
    }

    /**
     * @param sort {@link #SORTS} 중 하나 (대소문자 무시), null 이면 DATE.
     *             DATE: 진행 예정은 시작일 가까운 순, 지난 행사는 최근에 끝난 순.
     *             DEADLINE: 신청 마감이 남은 행사를 마감 임박 순으로 먼저, 나머지는 날짜순 (지난 행사에선 DATE 와 같다).
     *             LATEST: 최근 등록 순.
     */
    public PageResponse<EventSummaryResponse> list(Long userId, String region, String category, LocalDate from,
                                                   LocalDate to, boolean past, String sort, int page, int size) {
        if (region != null && !REGIONS.contains(region)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "region 은 SIHEUNG 또는 GYEONGGI 입니다.");
        }
        String sortValue = sort == null || sort.isBlank() ? "DATE" : sort.strip().toUpperCase(java.util.Locale.ROOT);
        if (!SORTS.contains(sortValue)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "sort 는 DATE, DEADLINE, LATEST 중 하나입니다.");
        }
        EventCategory categoryFilter = category(category);
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "from 은 to 보다 늦을 수 없습니다.");
        }
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        LocalDate today = LocalDate.now(clock);
        LocalDate start = from != null ? from : MIN_DATE;
        LocalDate end = to != null ? to : MAX_DATE;
        boolean anyCategory = categoryFilter == null;
        EventCategory categoryValue = anyCategory ? EventCategory.EVENT : categoryFilter; // anyCategory 면 쓰지 않는 값
        Page<Event> events = past
                ? eventRepository.findPast(today, region == null, region == null ? "" : region, anyCategory,
                categoryValue, start, end, sortValue, pageable)
                : eventRepository.findUpcoming(today, region == null, region == null ? "" : region, anyCategory,
                categoryValue, start, end, sortValue, java.time.OffsetDateTime.now(clock), pageable);
        String language = language(userId);
        Map<Long, EventLocalizer.Text> texts = localizer.texts(ids(events.getContent()), language);
        return PageResponse.from(events, event -> summary(event, texts.get(event.getId()), language));
    }

    /** EVE-04 상세. 지운 행사(DELETED)는 404 */
    public EventDetailResponse detail(Long userId, Long eventId) {
        Event event = eventRepository.findById(eventId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));
        EventLocalizer.Text text = localizer.text(eventId, language(userId));
        if (text == null) {
            throw new BusinessException(ErrorCode.EVENT_NOT_FOUND);
        }
        boolean subscribed = subscriptionRepository.existsByUserIdAndEventIdAndEnabledTrue(userId, eventId);
        return new EventDetailResponse(event.getId(), text.title(), text.description(), text.venue(),
                event.getStartsOn(), event.getEndsOn(), event.getStartsAt(), event.getEndsAt(), event.isTimeUnknown(),
                event.getPriceInfo(), event.getApplicationDeadline(), event.getApplicationUrl(), event.getSourceUrl(),
                event.getRegionCode(), event.getCategory().name(), event.getCategory().displayName(language(userId)),
                event.isMulticultural(), subscribed, text.language(), text.translated(), event.getThumbnailUrl());
    }

    /**
     * EVE-05 추천 (진행 예정·진행 중만). 온보딩 답으로 태그를 정한다:
     * 거주 정보 KIDS → FAMILY_KIDS, KOREAN → KOREAN_CLASS, 사용자 유형 NEW → NEWCOMER.
     * 순서: 맞는 태그가 많은 행사 → 다문화 → 시흥 → 시작일 가까운 순. 온보딩 전이면 태그 없이 다문화 → 시흥 → 시작일.
     */
    public List<EventRecommendationResponse> recommendations(Long userId, int size) {
        int limit = Math.min(Math.max(size, 1), 20);
        AppUser user = userRepository.findById(userId).orElse(null);
        Set<String> wanted = wantedTags(user);
        List<Event> candidates = eventRepository.findCurrent(LocalDate.now(clock));
        Map<Long, Set<String>> tags = new HashMap<>();
        if (!wanted.isEmpty() && !candidates.isEmpty()) {
            for (EventTag tag : tagRepository.findByEventIdIn(ids(candidates))) {
                tags.computeIfAbsent(tag.getEventId(), id -> new LinkedHashSet<>()).add(tag.getTag());
            }
        }
        record Scored(Event event, List<String> matched) {
        }
        List<Scored> ranked = candidates.stream()
                .map(event -> new Scored(event, TAG_ORDER.stream()
                        .filter(wanted::contains)
                        .filter(tag -> tags.getOrDefault(event.getId(), Set.of()).contains(tag))
                        .toList()))
                .sorted(Comparator.<Scored>comparingInt(scored -> -scored.matched().size())
                        .thenComparing(scored -> !scored.event().isMulticultural())
                        .thenComparing(scored -> !EventRegion.SIHEUNG.equals(scored.event().getRegionCode()))
                        .thenComparing(scored -> startDate(scored.event()),
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(scored -> scored.event().getId()))
                .limit(limit)
                .toList();
        String language = language(user);
        Map<Long, EventLocalizer.Text> texts = localizer.texts(ranked.stream().map(s -> s.event().getId()).toList(),
                language);
        return ranked.stream()
                .filter(scored -> texts.containsKey(scored.event().getId()))
                .map(scored -> new EventRecommendationResponse(
                        summary(scored.event(), texts.get(scored.event().getId()), language),
                        reasons(scored.event(), scored.matched())))
                .toList();
    }

    /** EVE-03 달력: 그 달에 하루라도 걸치는 행사 (지난 행사 포함). 시작일 순 */
    public List<EventCalendarItemResponse> calendar(Long userId, int year, int month) {
        return calendar(userId, year, month, null);
    }

    /** @param category {@link EventCategory} 코드, null 이면 전체 */
    public List<EventCalendarItemResponse> calendar(Long userId, int year, int month, String category) {
        if (year < 2000 || year > 2100 || month < 1 || month > 12) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "year 는 2000~2100, month 는 1~12 입니다.");
        }
        EventCategory categoryFilter = category(category);
        YearMonth yearMonth = YearMonth.of(year, month);
        List<Event> events = eventRepository.findOverlapping(yearMonth.atDay(1), yearMonth.atEndOfMonth(),
                categoryFilter == null, categoryFilter == null ? EventCategory.EVENT : categoryFilter);
        String language = language(userId);
        Map<Long, EventLocalizer.Text> texts = localizer.texts(ids(events), language);
        return events.stream()
                .filter(event -> texts.containsKey(event.getId()))
                .map(event -> {
                    EventLocalizer.Text text = texts.get(event.getId());
                    return new EventCalendarItemResponse(event.getId(), text.title(), event.getStartsOn(),
                            event.getEndsOn(), event.getRegionCode(), event.getCategory().name(),
                            event.getCategory().displayName(language), text.translated());
                })
                .toList();
    }

    /** EVE-10 최종 갱신 시각: 출처별 마지막 성공·마지막 실행 */
    public EventCollectionStatusResponse collectionStatus() {
        List<EventCollectionStatusResponse.SourceStatus> sources = new ArrayList<>();
        OffsetDateTime lastUpdatedAt = null;
        for (EventSourceType source : EventSourceType.values()) {
            OffsetDateTime succeededAt = runRepository
                    .findFirstBySourceAndStatusOrderByFinishedAtDesc(source.name(), EventCollectionRun.SUCCEEDED)
                    .map(EventCollectionRun::getFinishedAt)
                    .orElse(null);
            EventCollectionRunResponse lastRun = runRepository.findFirstBySourceOrderByIdDesc(source.name())
                    .map(EventCollectionRunResponse::from)
                    .orElse(null);
            sources.add(new EventCollectionStatusResponse.SourceStatus(source.name(), succeededAt, lastRun));
            if (succeededAt != null && (lastUpdatedAt == null || succeededAt.isAfter(lastUpdatedAt))) {
                lastUpdatedAt = succeededAt;
            }
        }
        return new EventCollectionStatusResponse(lastUpdatedAt, sources);
    }

    // ----------------------------------------------------------------

    private static Set<String> wantedTags(AppUser user) {
        Set<String> wanted = new LinkedHashSet<>();
        if (user == null) {
            return wanted;
        }
        if (user.hasResidence(Residence.KIDS)) {
            wanted.add(EventTag.FAMILY_KIDS);
        }
        if (user.hasResidence(Residence.KOREAN)) {
            wanted.add(EventTag.KOREAN_CLASS);
        }
        if (user.getUserType() == UserType.NEW) {
            wanted.add(EventTag.NEWCOMER);
        }
        return wanted;
    }

    private static List<String> reasons(Event event, List<String> matched) {
        List<String> reasons = new ArrayList<>(matched);
        if (event.isMulticultural()) {
            reasons.add(REASON_MULTICULTURAL);
        }
        if (EventRegion.SIHEUNG.equals(event.getRegionCode())) {
            reasons.add(REASON_SIHEUNG);
        }
        return reasons;
    }

    private static LocalDate startDate(Event event) {
        return event.getStartsOn() != null ? event.getStartsOn() : event.getEndsOn();
    }

    private static List<Long> ids(List<Event> events) {
        return events.stream().map(Event::getId).toList();
    }

    private String language(Long userId) {
        return language(userRepository.findById(userId).orElse(null));
    }

    private static String language(AppUser user) {
        return user == null ? SupportedLanguages.DEFAULT : SupportedLanguages.orDefault(user.getLanguage());
    }

    /** 원문 행이 없는 행사(정상이면 없음)는 빈 제목으로 */
    /** @param language 회원 화면 언어 (카테고리 이름용. 글은 번역이 없으면 한국어) */
    static EventSummaryResponse summary(Event event, EventLocalizer.Text found, String language) {
        EventLocalizer.Text text = Objects.requireNonNullElse(found,
                new EventLocalizer.Text("", null, null, SupportedLanguages.DEFAULT, false));
        return new EventSummaryResponse(event.getId(), text.title(), text.venue(), event.getStartsOn(),
                event.getEndsOn(), event.getStartsAt(), event.getEndsAt(), event.isTimeUnknown(),
                event.getRegionCode(), event.getCategory().name(), event.getCategory().displayName(language),
                event.isMulticultural(), text.language(), text.translated(), event.getThumbnailUrl());
    }

    /** 요청한 카테고리 코드. 비어 있으면 null (전체), 모르는 값이면 400 */
    private static EventCategory category(String value) {
        try {
            return EventCategory.parse(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "category 는 FESTIVAL, PERFORMANCE, EXHIBITION, EDUCATION, EVENT 중 하나입니다.");
        }
    }
}
