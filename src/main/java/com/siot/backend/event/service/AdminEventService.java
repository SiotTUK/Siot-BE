package com.siot.backend.event.service;

import com.siot.backend.event.collect.EventChangeNotifier;
import com.siot.backend.event.collect.EventChangeNotifier.ScheduleChange;
import com.siot.backend.event.collect.EventCollectionService;
import com.siot.backend.event.collect.EventKeywords;
import com.siot.backend.event.collect.EventNames;
import com.siot.backend.event.collect.EventTexts;
import com.siot.backend.event.collect.EventTranslationUpdater;
import com.siot.backend.event.collect.EventUpsertService;
import com.siot.backend.event.domain.Event;
import com.siot.backend.event.domain.EventCategory;
import com.siot.backend.event.domain.EventTag;
import com.siot.backend.event.domain.EventTranslation;
import com.siot.backend.event.dto.AdminEventCreateRequest;
import com.siot.backend.event.dto.AdminEventResponse;
import com.siot.backend.event.dto.AdminEventSummaryResponse;
import com.siot.backend.event.dto.AdminEventUpdateRequest;
import com.siot.backend.event.dto.EventCollectionRunResponse;
import com.siot.backend.event.repository.EventCollectionRunRepository;
import com.siot.backend.event.repository.EventRepository;
import com.siot.backend.event.repository.EventTagRepository;
import com.siot.backend.event.repository.EventTranslationRepository;
import com.siot.backend.global.common.PageResponse;
import com.siot.backend.global.common.SupportedLanguages;
import com.siot.backend.global.exception.BusinessException;
import com.siot.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 관리자 행사 관리 (ADM-05, EVE-07~09).
 * <ul>
 *     <li>등록: external_source=MANUAL. 행사명·시작일·장소가 같은 ACTIVE 행사가 있으면 409 (force 면 등록).
 *         ko 원문을 저장한 뒤 트랜잭션 밖에서 en/zh/vi 를 번역한다. 실패해도 등록은 성공이고 다음 수집 주기에 다시 번역한다</li>
 *     <li>수정: 보낸 칸만 바꾸고 잠근다(수집이 덮어쓰지 않게). 내용이 바뀌면 revision +1 (번역을 다시 만든다).
 *         일정이 바뀌면 커밋 뒤 {@link EventChangeNotifier}</li>
 *     <li>삭제: status=DELETED. 행은 남기고, 수집이 다시 살리지 않는다</li>
 *     <li>태그는 관리자가 고치지 않고 행사명 키워드로 다시 계산한다</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminEventService {

    private static final String ORIGINAL = SupportedLanguages.DEFAULT;
    private static final Set<String> STATUSES = Set.of(Event.STATUS_ACTIVE, Event.STATUS_DELETED);

    private final EventRepository eventRepository;
    private final EventTranslationRepository translationRepository;
    private final EventTagRepository tagRepository;
    private final EventCollectionRunRepository runRepository;
    private final EventUpsertService upsertService;
    private final EventTranslationUpdater translationUpdater;
    private final EventCollectionService collectionService;
    private final EventChangeNotifier changeNotifier;
    private final TransactionTemplate transactionTemplate;

    // ---------------------------------------------------------------- 조회

    /** 목록 (DELETED 포함). 조건이 null 이면 거르지 않는다. 최근 등록 순 */
    public PageResponse<AdminEventSummaryResponse> list(String status, String region, String source, String title,
                                                        int page, int size) {
        return list(status, region, source, title, null, page, size);
    }

    /** @param category {@link EventCategory} 코드, null 이면 전체 */
    public PageResponse<AdminEventSummaryResponse> list(String status, String region, String source, String title,
                                                        String category, int page, int size) {
        EventCategory categoryFilter;
        try {
            categoryFilter = EventCategory.parse(category);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "category 는 FESTIVAL, PERFORMANCE, EXHIBITION, EDUCATION, EVENT 중 하나입니다.");
        }
        if (region != null && !EventQueryService.REGIONS.contains(region)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "region 은 SIHEUNG 또는 GYEONGGI 입니다.");
        }
        if (status != null && !STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "status 는 ACTIVE 또는 DELETED 입니다.");
        }
        String keyword = EventTexts.clean(title);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return transactionTemplate.execute(tx -> {
            Page<Event> events = eventRepository.search(status == null, Objects.toString(status, ""),
                    region == null, Objects.toString(region, ""),
                    categoryFilter == null, categoryFilter == null ? EventCategory.EVENT : categoryFilter,
                    source == null, Objects.toString(source, ""),
                    keyword == null, keyword == null ? "" : likePattern(keyword), pageable);
            Map<Long, EventTranslation> originals = originals(events.getContent());
            return PageResponse.from(events, event -> new AdminEventSummaryResponse(event.getId(),
                    title(originals.get(event.getId())), event.getStartsOn(), event.getEndsOn(),
                    event.getRegionCode(), event.getExternalSource(), event.getExternalId(), event.getStatus(),
                    event.isMulticultural(), event.getCategory().name(), event.getRevision(), event.getUpdatedAt()));
        });
    }

    /** 상세 (DELETED 포함) */
    public AdminEventResponse get(Long eventId) {
        return transactionTemplate.execute(tx -> response(find(eventId)));
    }

    // ---------------------------------------------------------------- 등록 (EVE-07)

    public AdminEventResponse create(Long adminId, AdminEventCreateRequest request, boolean force) {
        String title = EventTexts.title(request.title());
        if (title == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "행사명을 입력해 주세요.");
        }
        LocalDate startsOn = request.startsOn();
        LocalDate endsOn = request.endsOn() != null ? request.endsOn() : startsOn;
        validateDates(startsOn, endsOn);
        String description = text(request.description());
        String venue = EventTexts.clean(request.venue());

        Long eventId = transactionTemplate.execute(tx -> {
            if (!force) {
                List<Long> duplicates = findDuplicates(title, startsOn != null ? startsOn : endsOn, venue);
                if (!duplicates.isEmpty()) {
                    throw new BusinessException(ErrorCode.DUPLICATE_EVENT, Map.of("eventIds", duplicates));
                }
            }
            Event event = Event.manual(adminId);
            event.editStartsOn(startsOn, false);
            event.editEndsOn(endsOn, false);
            event.editStartsAt(request.startsAt(), false);
            event.editEndsAt(request.endsAt(), false);
            event.editApplicationDeadline(request.applicationDeadline(), false);
            event.editPriceInfo(EventTexts.clean(request.priceInfo()), false);
            event.editApplicationUrl(EventTexts.clean(request.applicationUrl()), false);
            event.editSourceUrl(EventTexts.clean(request.sourceUrl()), false);
            event.editRegionCode(request.region(), false);
            // 관리자가 정한 다문화 여부는 잠근다 (다른 출처의 같은 행사가 빈 칸을 채울 때 키워드로 다시 계산하지 않게)
            if (request.multicultural() != null) {
                event.editMulticultural(request.multicultural(), true);
            } else {
                event.editMulticultural(EventKeywords.multicultural(title, description), false);
            }
            // 관리자 등록 행사는 수집 대상이 아니라 잠그지 않는다. 비우면 EVENT
            event.editCategory(request.category() == null ? EventCategory.EVENT : EventCategory.parse(request.category()),
                    false);
            eventRepository.save(event);
            translationRepository.save(new EventTranslation(event.getId(), ORIGINAL, title, description, venue,
                    event.getRevision()));
            upsertService.syncTags(event.getId(), title);
            return event.getId();
        });
        translate(eventId);
        return get(eventId);
    }

    /** 저장 직후 번역. 실패하면 source_revision 이 그대로라 다음 수집 주기에 다시 한다 */
    private void translate(Long eventId) {
        try {
            translationUpdater.translate(eventId);
        } catch (RuntimeException e) {
            log.warn("Event {} translation failed, will retry next collection cycle: {}", eventId, e.getMessage());
        }
    }

    /**
     * 행사명(연도·"제N회"·공백·기호를 뺀 이름)과 시작일이 같고, 장소가 같거나 한쪽 장소가 비어 있는 ACTIVE 행사.
     * 수집 행사는 장소가 없는 경우가 많아 장소가 비어 있으면 이름·날짜만으로 본다.
     */
    private List<Long> findDuplicates(String title, LocalDate startsOn, String venue) {
        List<Event> sameDay = eventRepository.findActiveStartingOn(startsOn);
        if (sameDay.isEmpty()) {
            return List.of();
        }
        String name = EventNames.normalize(title);
        String place = EventNames.normalize(venue);
        return translationRepository.findByEventIdInAndLanguage(ids(sameDay), ORIGINAL).stream()
                .filter(original -> !name.isEmpty() && name.equals(EventNames.normalize(original.getTitle())))
                .filter(original -> {
                    String other = EventNames.normalize(original.getVenue());
                    return place.isEmpty() || other.isEmpty() || place.equals(other);
                })
                .map(EventTranslation::getEventId)
                .sorted()
                .toList();
    }

    // ---------------------------------------------------------------- 수정 (EVE-08)

    public AdminEventResponse update(Long adminId, Long eventId, AdminEventUpdateRequest request) {
        ScheduleChange change = transactionTemplate.execute(tx -> {
            Event event = find(eventId);
            EventTranslation original = translationRepository.findOriginal(eventId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));
            LocalDate oldStartsOn = event.getStartsOn();
            LocalDate oldEndsOn = event.getEndsOn();
            LocalTime oldStartsAt = event.getStartsAt();
            LocalTime oldEndsAt = event.getEndsAt();

            boolean changed = false;
            if (request.startsOn() != null) {
                changed |= event.editStartsOn(request.startsOn(), true);
            }
            if (request.endsOn() != null) {
                changed |= event.editEndsOn(request.endsOn(), true);
            }
            if (request.startsAt() != null) {
                changed |= event.editStartsAt(request.startsAt(), true);
            }
            if (request.endsAt() != null) {
                changed |= event.editEndsAt(request.endsAt(), true);
            }
            if (request.applicationDeadline() != null) {
                changed |= event.editApplicationDeadline(request.applicationDeadline(), true);
            }
            if (request.priceInfo() != null) {
                changed |= event.editPriceInfo(EventTexts.clean(request.priceInfo()), true);
            }
            if (request.applicationUrl() != null) {
                changed |= event.editApplicationUrl(EventTexts.clean(request.applicationUrl()), true);
            }
            if (request.sourceUrl() != null) {
                changed |= event.editSourceUrl(EventTexts.clean(request.sourceUrl()), true);
            }
            if (request.region() != null) {
                changed |= event.editRegionCode(request.region(), true);
            }
            boolean textChanged = false;
            if (request.title() != null) {
                String title = EventTexts.title(request.title());
                if (title == null) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT, "행사명을 비울 수 없습니다.");
                }
                textChanged |= original.editTitle(title, true);
            }
            if (request.description() != null) {
                textChanged |= original.editDescription(text(request.description()), true);
            }
            if (request.venue() != null) {
                textChanged |= original.editVenue(EventTexts.clean(request.venue()), true);
            }
            if (request.multicultural() != null) {
                changed |= event.editMulticultural(request.multicultural(), true);
            } else if (textChanged) {
                changed |= event.recomputeMulticultural(
                        EventKeywords.multicultural(original.getTitle(), original.getDescription()));
            }
            changed |= textChanged;
            validateDates(event.getStartsOn(), event.getEndsOn());
            // 카테고리는 잠가서 수집이 덮어쓰지 않게 한다. 번역할 글이 아니라 revision 은 올리지 않는다
            if (request.category() != null) {
                event.editCategory(EventCategory.parse(request.category()), true);
            }

            event.editedBy(adminId);
            if (changed) {
                event.increaseRevision();
                original.markSourceRevision(event.getRevision());
            }
            upsertService.syncTags(eventId, original.getTitle());
            boolean scheduleChanged = !Objects.equals(oldStartsOn, event.getStartsOn())
                    || !Objects.equals(oldEndsOn, event.getEndsOn())
                    || !Objects.equals(oldStartsAt, event.getStartsAt())
                    || !Objects.equals(oldEndsAt, event.getEndsAt());
            return scheduleChanged && !event.isDeleted()
                    ? new ScheduleChange(eventId, oldStartsOn, oldEndsOn, oldStartsAt, oldEndsAt,
                    event.getStartsOn(), event.getEndsOn(), event.getStartsAt(), event.getEndsAt())
                    : null;
        });
        if (change != null) {
            try {
                changeNotifier.scheduleChanged(change);
            } catch (RuntimeException e) {
                log.warn("Event schedule change notification failed for event {}: {}", eventId, e.getMessage());
            }
        }
        return get(eventId);
    }

    // ---------------------------------------------------------------- 삭제 (EVE-09)

    /** status=DELETED (이미 지웠으면 그대로) */
    public void delete(Long adminId, Long eventId) {
        transactionTemplate.executeWithoutResult(tx -> find(eventId).delete(adminId));
    }

    // ---------------------------------------------------------------- 수동 수집

    /** 지금 한 번 수집 (스케줄과 같은 코드). 출처 수집이 끝나면 돌려주고 번역은 뒤에서 한다 */
    public List<EventCollectionRunResponse> collect() {
        List<Long> runIds;
        try {
            runIds = collectionService.collectNow();
        } catch (EventCollectionService.AlreadyRunningException e) {
            throw new BusinessException(ErrorCode.EVENT_COLLECTION_RUNNING);
        }
        return runRepository.findAllById(runIds).stream()
                .sorted((a, b) -> Integer.compare(runIds.indexOf(a.getId()), runIds.indexOf(b.getId())))
                .map(EventCollectionRunResponse::from)
                .toList();
    }

    // ----------------------------------------------------------------

    private Event find(Long eventId) {
        return eventRepository.findById(eventId).orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));
    }

    private AdminEventResponse response(Event event) {
        List<EventTranslation> rows = translationRepository.findByEventId(event.getId());
        EventTranslation original = rows.stream().filter(row -> ORIGINAL.equals(row.getLanguage())).findFirst()
                .orElse(null);
        List<String> translated = rows.stream()
                .filter(row -> Objects.equals(row.getSourceRevision(), event.getRevision()))
                .map(EventTranslation::getLanguage)
                .sorted((a, b) -> Integer.compare(languageOrder(a), languageOrder(b)))
                .toList();
        List<String> tags = tagRepository.findByEventId(event.getId()).stream().map(EventTag::getTag).sorted()
                .toList();
        return new AdminEventResponse(event.getId(), title(original),
                original == null ? null : original.getDescription(), original == null ? null : original.getVenue(),
                event.getStartsOn(), event.getEndsOn(), event.getStartsAt(), event.getEndsAt(), event.isTimeUnknown(),
                event.getPriceInfo(), event.getApplicationDeadline(), event.getApplicationUrl(), event.getSourceUrl(),
                event.getRegionCode(), event.isMulticultural(), event.getCategory().name(), event.getExternalSource(),
                event.getExternalId(), event.getStatus(), event.getRevision(), lockedFields(event, original), tags,
                translated,
                event.getCreatedAt(), event.getUpdatedAt());
    }

    /** 잠긴 칸 (요청 필드 이름) */
    private static List<String> lockedFields(Event event, EventTranslation original) {
        List<String> locked = new ArrayList<>();
        if (original != null && original.isTitleLocked()) {
            locked.add("title");
        }
        if (original != null && original.isDescriptionLocked()) {
            locked.add("description");
        }
        if (original != null && original.isVenueLocked()) {
            locked.add("venue");
        }
        addIf(locked, event.isStartsOnLocked(), "startsOn");
        addIf(locked, event.isEndsOnLocked(), "endsOn");
        addIf(locked, event.isStartsAtLocked(), "startsAt");
        addIf(locked, event.isEndsAtLocked(), "endsAt");
        addIf(locked, event.isPriceInfoLocked(), "priceInfo");
        addIf(locked, event.isDeadlineLocked(), "applicationDeadline");
        addIf(locked, event.isApplicationUrlLocked(), "applicationUrl");
        addIf(locked, event.isSourceUrlLocked(), "sourceUrl");
        addIf(locked, event.isRegionCodeLocked(), "region");
        addIf(locked, event.isMulticulturalLocked(), "multicultural");
        addIf(locked, event.isCategoryLocked(), "category");
        return locked;
    }

    private static void addIf(List<String> list, boolean condition, String value) {
        if (condition) {
            list.add(value);
        }
    }

    private static int languageOrder(String language) {
        int index = List.of("ko", "en", "zh", "vi").indexOf(language);
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    private Map<Long, EventTranslation> originals(List<Event> events) {
        if (events.isEmpty()) {
            return Map.of();
        }
        return translationRepository.findByEventIdInAndLanguage(ids(events), ORIGINAL).stream()
                .collect(Collectors.toMap(EventTranslation::getEventId, Function.identity()));
    }

    private static String title(EventTranslation original) {
        return original == null ? "" : original.getTitle();
    }

    private static List<Long> ids(List<Event> events) {
        return events.stream().map(Event::getId).toList();
    }

    /** 시작일·종료일 중 하나는 있어야 하고, 시작일이 종료일보다 늦을 수 없다 */
    private static void validateDates(LocalDate startsOn, LocalDate endsOn) {
        if (startsOn == null && endsOn == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "시작일과 종료일 중 하나는 입력해 주세요.");
        }
        if (startsOn != null && endsOn != null && startsOn.isAfter(endsOn)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "시작일은 종료일보다 늦을 수 없습니다.");
        }
    }

    /** 여러 줄 글 (설명): 앞뒤 공백만 뺀다. 비면 null */
    private static String text(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    /** LIKE 패턴 (소문자, 이스케이프 문자 !) */
    private static String likePattern(String keyword) {
        String escaped = keyword.toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }
}
