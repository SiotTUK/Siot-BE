package com.siot.backend.event.collect;

import com.siot.backend.event.collect.EventChangeNotifier.ScheduleChange;
import com.siot.backend.event.domain.Event;
import com.siot.backend.event.domain.EventTag;
import com.siot.backend.event.domain.EventTranslation;
import com.siot.backend.event.repository.EventRepository;
import com.siot.backend.event.repository.EventTagRepository;
import com.siot.backend.event.repository.EventTranslationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 받은 행사를 저장한다 (EVE-01). 한 출처를 한 트랜잭션으로.
 * <ul>
 *     <li>(external_source, external_id) 로 찾아 갱신한다. event_id 는 바뀌지 않는다</li>
 *     <li>관리자가 지운 행사(DELETED)는 건드리지 않고, 잠긴 칸(*_locked)은 덮어쓰지 않는다</li>
 *     <li>내용이 바뀌면 revision +1 (번역을 다시 만든다). ko 번역 행에 원문과 source_revision</li>
 *     <li>처음 보는 행사가 다른 출처(또는 같은 출처의 다른 ID)에 이미 있으면 (행사명에서 연도·"제N회"·공백·기호를 뺀 이름 +
 *         시작일이 같음) 새로 만들지 않고 먼저 저장된 행사의 빈 칸(설명·장소)만 채운다</li>
 *     <li>is_multicultural·event_tag 는 키워드 규칙으로 다시 계산한다</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventUpsertService {

    static final String ORIGINAL_LANGUAGE = "ko";

    private final EventRepository eventRepository;
    private final EventTranslationRepository translationRepository;
    private final EventTagRepository tagRepository;

    /**
     * @param upserted 새로 만들었거나 내용이 바뀐 행사 수
     * @param changes  일정이 바뀐 기존 행사 (커밋 뒤 알림으로 넘긴다)
     */
    public record Result(int upserted, List<ScheduleChange> changes) {
    }

    @Transactional
    public Result upsert(EventSourceType source, List<CollectedEvent> events) {
        int upserted = 0;
        List<ScheduleChange> changes = new ArrayList<>();
        for (CollectedEvent collected : events) {
            Optional<Event> existing = eventRepository.findByExternalSourceAndExternalId(source.name(),
                    collected.externalId());
            if (existing.isPresent()) {
                if (!existing.get().isDeleted() && update(existing.get(), collected, changes)) {
                    upserted++;
                }
                continue;
            }
            Optional<Event> duplicate = findDuplicate(collected);
            if (duplicate.isPresent()) {
                if (!duplicate.get().isDeleted() && fillBlanks(duplicate.get(), collected)) {
                    upserted++;
                }
                continue;
            }
            create(source, collected);
            upserted++;
        }
        return new Result(upserted, changes);
    }

    private void create(EventSourceType source, CollectedEvent collected) {
        Event event = Event.collected(source.name(), collected.externalId());
        event.collectSchedule(collected.startsOn(), collected.endsOn(), collected.startsAt(), collected.endsAt());
        event.collectDetails(collected.regionCode(), collected.priceInfo(), collected.sourceUrl(),
                EventKeywords.multicultural(collected.title(), collected.description()));
        eventRepository.save(event);
        translationRepository.save(new EventTranslation(event.getId(), ORIGINAL_LANGUAGE, collected.title(),
                collected.description(), collected.venue(), event.getRevision()));
        syncTags(event.getId(), collected.title());
    }

    private boolean update(Event event, CollectedEvent collected, List<ScheduleChange> changes) {
        LocalDate oldStartsOn = event.getStartsOn();
        LocalDate oldEndsOn = event.getEndsOn();
        LocalTime oldStartsAt = event.getStartsAt();
        LocalTime oldEndsAt = event.getEndsAt();
        EventTranslation original = translationRepository.findOriginal(event.getId())
                .orElseGet(() -> translationRepository.save(new EventTranslation(event.getId(), ORIGINAL_LANGUAGE,
                        collected.title(), null, null, null)));
        String description = collected.description() != null ? collected.description() : original.getDescription();

        boolean changed = event.collectSchedule(collected.startsOn(), collected.endsOn(), collected.startsAt(),
                collected.endsAt());
        boolean scheduleChanged = !Objects.equals(oldStartsOn, event.getStartsOn())
                || !Objects.equals(oldEndsOn, event.getEndsOn())
                || !Objects.equals(oldStartsAt, event.getStartsAt())
                || !Objects.equals(oldEndsAt, event.getEndsAt());
        changed |= event.collectDetails(collected.regionCode(), collected.priceInfo(), collected.sourceUrl(),
                EventKeywords.multicultural(collected.title(), description));
        changed |= original.collectOriginal(collected.title(), collected.description(), collected.venue());
        if (changed) {
            event.increaseRevision();
            original.markSourceRevision(event.getRevision());
        }
        if (scheduleChanged) {
            changes.add(new ScheduleChange(event.getId(), oldStartsOn, oldEndsOn, oldStartsAt, oldEndsAt,
                    event.getStartsOn(), event.getEndsOn(), event.getStartsAt(), event.getEndsAt()));
        }
        syncTags(event.getId(), original.getTitle());
        return changed;
    }

    /** 먼저 저장된 같은 행사의 빈 칸(설명·장소)만 채운다 */
    private boolean fillBlanks(Event event, CollectedEvent collected) {
        Optional<EventTranslation> original = translationRepository.findOriginal(event.getId());
        if (original.isEmpty() || !original.get().fillBlanks(collected.description(), collected.venue())) {
            return false;
        }
        event.collectDetails(null, null, null,
                EventKeywords.multicultural(original.get().getTitle(), original.get().getDescription()));
        event.increaseRevision();
        original.get().markSourceRevision(event.getRevision());
        log.info("Event {} filled from duplicate {} ({})", event.getId(), collected.externalId(), collected.title());
        return true;
    }

    /** 시작일이 같고 정규화한 행사명이 같은 행사 (상태 무관: 지운 행사도 다시 만들지 않게) */
    Optional<Event> findDuplicate(CollectedEvent collected) {
        List<Event> sameDay = eventRepository.findByStartsOn(collected.startsOn());
        if (sameDay.isEmpty()) {
            return Optional.empty();
        }
        Map<Long, Event> byId = sameDay.stream().collect(Collectors.toMap(Event::getId, Function.identity()));
        String name = EventNames.normalize(collected.title());
        return translationRepository.findByEventIdInAndLanguage(byId.keySet(), ORIGINAL_LANGUAGE).stream()
                .filter(original -> !name.isEmpty() && name.equals(EventNames.normalize(original.getTitle())))
                .map(original -> byId.get(original.getEventId()))
                .min((a, b) -> Long.compare(a.getId(), b.getId()));
    }

    /** 행사명 키워드 태그로 맞춘다 */
    private void syncTags(Long eventId, String title) {
        Set<String> wanted = EventKeywords.tags(title);
        List<EventTag> current = tagRepository.findByEventId(eventId);
        Set<String> have = current.stream().map(EventTag::getTag).collect(Collectors.toSet());
        if (have.equals(wanted)) {
            return;
        }
        tagRepository.deleteAll(current.stream().filter(tag -> !wanted.contains(tag.getTag())).toList());
        tagRepository.saveAll(wanted.stream().filter(tag -> !have.contains(tag))
                .map(tag -> new EventTag(eventId, tag)).toList());
    }
}
