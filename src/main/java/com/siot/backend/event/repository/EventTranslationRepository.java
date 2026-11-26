package com.siot.backend.event.repository;

import com.siot.backend.event.domain.EventTranslation;
import com.siot.backend.event.domain.EventTranslationId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EventTranslationRepository extends JpaRepository<EventTranslation, EventTranslationId> {

    Optional<EventTranslation> findByEventIdAndLanguage(Long eventId, String language);

    List<EventTranslation> findByEventId(Long eventId);

    List<EventTranslation> findByEventIdInAndLanguage(Collection<Long> eventIds, String language);

    List<EventTranslation> findByEventIdInAndLanguageIn(Collection<Long> eventIds, Collection<String> languages);

    default Optional<EventTranslation> findOriginal(Long eventId) {
        return findById(new EventTranslationId("ko", eventId));
    }
}
