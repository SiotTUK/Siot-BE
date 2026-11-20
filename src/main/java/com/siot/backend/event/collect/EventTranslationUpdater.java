package com.siot.backend.event.collect;

import com.siot.backend.event.domain.Event;
import com.siot.backend.event.domain.EventTranslation;
import com.siot.backend.event.domain.EventTranslationId;
import com.siot.backend.event.repository.EventRepository;
import com.siot.backend.event.repository.EventTranslationRepository;
import com.siot.backend.rag.ai.AiUnavailableException;
import com.siot.backend.translation.Translator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 행사 번역 (EVE-02, COM-02). 번역 대상은 행사명·상세 설명·장소.
 * <ul>
 *     <li>보이는 행사(ACTIVE, 끝나지 않음) 중 en/zh/vi 번역이 없거나 source_revision 이 event.revision 과 다른 것만</li>
 *     <li>Gemini 호출은 트랜잭션 밖에서, 호출 사이에 쉰다 (무료 등급 분당 한도)</li>
 *     <li>번역이 실패하면(AiUnavailableException) 이번 주기는 멈추고 남은 행사는 다음 주기에 다시 한다.
 *         수집 결과(event_collection_run)는 실패로 보지 않는다</li>
 *     <li>관리자가 잠근 번역 칸(*_locked)은 덮어쓰지 않는다</li>
 * </ul>
 */
@Slf4j
public class EventTranslationUpdater {

    /** 테스트에서 쉬는 시간을 건너뛰기 위한 대기 */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final EventRepository eventRepository;
    private final EventTranslationRepository translationRepository;
    private final Translator translator;
    private final TransactionTemplate transactionTemplate;
    private final Duration pause;
    private final Sleeper sleeper;

    public EventTranslationUpdater(EventRepository eventRepository, EventTranslationRepository translationRepository,
                                   Translator translator, TransactionTemplate transactionTemplate, Duration pause,
                                   Sleeper sleeper) {
        this.eventRepository = eventRepository;
        this.translationRepository = translationRepository;
        this.translator = translator;
        this.transactionTemplate = transactionTemplate;
        this.pause = pause;
        this.sleeper = sleeper;
    }

    /** 원문과 revision 을 함께 읽은 것 */
    private record Original(int revision, String title, String description, String venue) {
    }

    /** @return 번역을 저장한 행사 수 */
    public int translatePending(LocalDate today) {
        List<Long> ids = eventRepository.findIdsNeedingTranslation(today);
        int translated = 0;
        boolean first = true;
        for (Long eventId : ids) {
            try {
                if (!first) {
                    sleeper.sleep(pause);
                }
                first = false;
                if (translate(eventId)) {
                    translated++;
                }
            } catch (AiUnavailableException e) {
                log.warn("Event translation stopped at event {} ({} of {} done), will retry next cycle: {}",
                        eventId, translated, ids.size(), e.getMessage());
                break;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return translated;
    }

    /**
     * 행사 하나의 en/zh/vi 번역을 지금 원문(ko)으로 만든다 (수집 주기, 관리자 등록 직후).
     * 트랜잭션 안에서 부르지 않는다 (Gemini 호출 동안 연결을 잡지 않게).
     *
     * @return 저장했으면 true. 행사·원문이 없거나 번역하는 동안 원문이 바뀌었으면 false
     * @throws AiUnavailableException 번역 실패 (source_revision 은 그대로라 다음 주기에 다시 한다)
     */
    public boolean translate(Long eventId) {
        Optional<Original> original = transactionTemplate.execute(status -> readOriginal(eventId));
        if (original == null || original.isEmpty()) {
            return false;
        }
        Map<String, String> titles = translateAll(original.get().title());
        Map<String, String> descriptions = translateAll(original.get().description());
        Map<String, String> venues = translateAll(original.get().venue());
        Boolean saved = transactionTemplate.execute(status ->
                save(eventId, original.get().revision(), titles, descriptions, venues));
        return Boolean.TRUE.equals(saved);
    }

    private Optional<Original> readOriginal(Long eventId) {
        Optional<Event> event = eventRepository.findById(eventId);
        Optional<EventTranslation> ko = translationRepository.findOriginal(eventId);
        if (event.isEmpty() || ko.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Original(event.get().getRevision(), ko.get().getTitle(), ko.get().getDescription(),
                ko.get().getVenue()));
    }

    /** 원문이 비어 있으면 호출하지 않고 언어마다 null */
    private Map<String, String> translateAll(String text) {
        if (text == null || text.isBlank()) {
            Map<String, String> empty = new HashMap<>();
            Translator.TARGET_LANGUAGES.forEach(language -> empty.put(language, null));
            return empty;
        }
        return translator.translateAll(text);
    }

    /** 번역하는 동안 원문이 다시 바뀌었으면 저장하지 않는다 (다음 주기에 새 원문으로) */
    private boolean save(Long eventId, int revision, Map<String, String> titles, Map<String, String> descriptions,
                         Map<String, String> venues) {
        Optional<Event> event = eventRepository.findById(eventId);
        if (event.isEmpty() || event.get().getRevision() != revision) {
            return false;
        }
        for (String language : Translator.TARGET_LANGUAGES) {
            String title = titles.get(language);
            Optional<EventTranslation> row = translationRepository.findById(new EventTranslationId(language, eventId));
            if (row.isPresent()) {
                row.get().applyTranslation(title, descriptions.get(language), venues.get(language), revision);
            } else if (title != null && !title.isBlank()) {
                translationRepository.save(new EventTranslation(eventId, language, title,
                        descriptions.get(language), venues.get(language), revision));
            }
        }
        return true;
    }
}
