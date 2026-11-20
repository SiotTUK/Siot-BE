package com.siot.backend.event.repository;

import com.siot.backend.event.domain.EventTranslation;
import com.siot.backend.event.domain.EventTranslationId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EventTranslationRepository extends JpaRepository<EventTranslation, EventTranslationId> {

    Optional<EventTranslation> findByEventIdAndLanguage(Long eventId, String language);
}
