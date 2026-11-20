package com.siot.backend.rag.repository;

import com.siot.backend.rag.domain.GuideDocumentReference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GuideDocumentReferenceRepository extends JpaRepository<GuideDocumentReference, Long> {

    List<GuideDocumentReference> findByGuideIdIn(Collection<Long> guideIds);

    Optional<GuideDocumentReference> findFirstByGuideIdOrderByCreatedAtDesc(Long guideId);

    List<GuideDocumentReference> findByGuideId(Long guideId);
}
