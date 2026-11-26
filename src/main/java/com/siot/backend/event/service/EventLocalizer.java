package com.siot.backend.event.service;

import com.siot.backend.event.domain.EventTranslation;
import com.siot.backend.event.repository.EventTranslationRepository;
import com.siot.backend.global.common.SupportedLanguages;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 행사 글(행사명·설명·장소)을 회원 언어로 고른다 (COM-02, EVE-02).
 * 회원 언어 행이 있으면 그것을, 없으면 한국어 원문을 translated=false 로 준다.
 * 원문이 바뀌어 번역을 다시 만드는 중(source_revision 이 다름)이어도 있는 번역을 보여 준다.
 */
@Component
@RequiredArgsConstructor
class EventLocalizer {

    private static final String ORIGINAL = SupportedLanguages.DEFAULT;

    private final EventTranslationRepository translationRepository;

    /** 고른 글 */
    record Text(String title, String description, String venue, String language, boolean translated) {
    }

    /** @return 행사 ID → 글. ko 원문 행도 없는 행사는 빠진다 */
    Map<Long, Text> texts(Collection<Long> eventIds, String language) {
        Map<Long, Text> result = new HashMap<>();
        if (eventIds.isEmpty()) {
            return result;
        }
        String wanted = SupportedLanguages.orDefault(language);
        List<EventTranslation> rows = translationRepository.findByEventIdInAndLanguageIn(eventIds,
                ORIGINAL.equals(wanted) ? Set.of(ORIGINAL) : Set.of(ORIGINAL, wanted));
        Map<Long, EventTranslation> originals = new HashMap<>();
        Map<Long, EventTranslation> translations = new HashMap<>();
        for (EventTranslation row : rows) {
            (ORIGINAL.equals(row.getLanguage()) ? originals : translations).put(row.getEventId(), row);
        }
        for (Long eventId : eventIds) {
            EventTranslation translated = ORIGINAL.equals(wanted) ? originals.get(eventId) : translations.get(eventId);
            if (translated != null) {
                result.put(eventId, new Text(translated.getTitle(), translated.getDescription(),
                        translated.getVenue(), wanted, true));
                continue;
            }
            EventTranslation original = originals.get(eventId);
            if (original != null) {
                result.put(eventId, new Text(original.getTitle(), original.getDescription(), original.getVenue(),
                        ORIGINAL, false));
            }
        }
        return result;
    }

    Text text(Long eventId, String language) {
        return texts(List.of(eventId), language).get(eventId);
    }
}
