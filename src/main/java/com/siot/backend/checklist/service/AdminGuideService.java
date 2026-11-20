package com.siot.backend.checklist.service;

import com.siot.backend.checklist.domain.Guide;
import com.siot.backend.checklist.domain.GuideTranslation;
import com.siot.backend.checklist.domain.GuideTranslationId;
import com.siot.backend.checklist.dto.GuideTranslationResponse;
import com.siot.backend.checklist.dto.GuideTranslationUpdateRequest;
import com.siot.backend.checklist.repository.GuideRepository;
import com.siot.backend.checklist.repository.GuideTranslationRepository;
import com.siot.backend.global.exception.BusinessException;
import com.siot.backend.global.exception.ErrorCode;
import com.siot.backend.notification.service.NotificationService;
import com.siot.backend.rag.domain.GuideDocumentReference;
import com.siot.backend.rag.repository.GuideDocumentReferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/** 관리자: 항목(체크리스트·생활 변화 할 일·생활수칙) 문구 저장 */
@Service
@RequiredArgsConstructor
public class AdminGuideService {

    private static final Set<String> LANGUAGES = Set.of("ko", "en", "zh", "vi");

    private final GuideRepository guideRepository;
    private final GuideTranslationRepository translationRepository;
    private final GuideDocumentReferenceRepository guideDocumentReferenceRepository;
    private final NotificationService notificationService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public GuideTranslationResponse get(String code, String language) {
        Guide guide = findGuide(code);
        GuideTranslation translation = translationRepository
                .findById(new GuideTranslationId(checkLanguage(language), guide.getId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.GUIDE_TRANSLATION_NOT_FOUND));
        return toResponse(guide, translation);
    }

    /**
     * 한 언어의 문구를 저장한다 (없으면 새로 만든다).
     * noticeChanged 가 true 면 guide.notice_changed_at 을 지금으로 기록한다 (NTF-07).
     */
    @Transactional
    public GuideTranslationResponse save(Long adminId, String code, String language,
                                         GuideTranslationUpdateRequest request) {
        Guide guide = findGuide(code);
        String lang = checkLanguage(language);
        // 새 언어는 값을 다 채운 뒤 저장한다 (title NOT NULL)
        GuideTranslation translation = translationRepository.findById(new GuideTranslationId(lang, guide.getId()))
                .orElseGet(() -> GuideTranslation.create(guide.getId(), lang));
        translation.update(request.title().strip(), blankToNull(request.summary()), blankToNull(request.content()),
                blankToNull(request.badge()), blankToNull(request.place()),
                request.docs() == null ? List.of() : request.docs().stream().map(String::strip).toList(),
                blankToNull(request.fee()), blankToNull(request.law()), blankToNull(request.penalty()),
                blankToNull(request.sourceName()));
        translation = translationRepository.save(translation);
        guide.recordEdit(adminId, request.noticeChanged(), OffsetDateTime.now(clock));
        if (request.noticeChanged()) {
            notificationService.createNoticeChangedNotifications(guide);
        }
        return toResponse(guide, translation);
    }

    private Guide findGuide(String code) {
        return guideRepository.findByCode(code)
                .filter(g -> !g.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.GUIDE_NOT_FOUND));
    }

    private static String checkLanguage(String language) {
        if (!LANGUAGES.contains(language)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return language;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private GuideTranslationResponse toResponse(Guide guide, GuideTranslation t) {
        GuideDocumentReference docRef = guideDocumentReferenceRepository
                .findFirstByGuideIdOrderByCreatedAtDesc(guide.getId())
                .orElse(null);
        Integer pageStart = docRef != null ? docRef.getPageStart() : null;
        Integer pageEnd = docRef != null ? docRef.getPageEnd() : null;
        String sourcePages = null;
        if (pageStart != null) {
            sourcePages = (pageEnd != null && !pageEnd.equals(pageStart))
                    ? String.format("p.%d~%d", pageStart, pageEnd)
                    : String.format("p.%d", pageStart);
        }

        return new GuideTranslationResponse(guide.getCode(), t.getLanguage(), t.getTitle(), t.getSummary(),
                t.getContent(), t.getBadge(), t.getPlace(),
                // 준비물이 없는 행은 DB 값이 NULL 이다
                t.getDocs() == null ? List.of() : List.copyOf(t.getDocs()), t.getFee(), t.getLaw(),
                t.getPenalty(), t.getSourceName(), guide.getNoticeChangedAt(), guide.getUpdatedAt(),
                pageStart, pageEnd, sourcePages);
    }
}
