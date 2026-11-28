package com.siot.backend.checklist.service;

import com.siot.backend.checklist.domain.Guide;
import com.siot.backend.checklist.domain.GuideTranslation;
import com.siot.backend.checklist.domain.GuideTranslationId;
import com.siot.backend.checklist.dto.GuideInfoResponse;
import com.siot.backend.checklist.dto.GuideInfoUpdateRequest;
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
    private final GuideTranslationGenerator guideTranslationGenerator;
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
        // #14 생활수칙 칸: 보낸 것만 바꾼다
        translation.updateRuleFields(
                request.points() != null ? request.points().stream().map(String::strip).toList() : translation.getPoints(),
                request.when() != null ? blankToNull(request.when()) : translation.getTiming(),
                request.contact() != null ? blankToNull(request.contact()) : translation.getContact());
        // #14 관리자가 직접 고친 번역은 사람이 확인한 것으로 보고 자동 번역이 덮어쓰지 않는다
        if (!"ko".equals(lang)) {
            translation.markReviewed(true);
        }
        translation = translationRepository.save(translation);
        // COM-02: 한국어 원문을 고치면 en/zh/vi 번역을 다시 만든다 (다른 언어를 고치면 그 언어만 바뀐다)
        if ("ko".equals(lang)) {
            guideTranslationGenerator.regenerate(translation);
        }
        guide.recordEdit(adminId, request.noticeChanged(), OffsetDateTime.now(clock));
        if (request.noticeChanged()) {
            notificationService.createNoticeChangedNotifications(guide);
        }
        return toResponse(guide, translation);
    }

    /**
     * 출처 링크·문의 전화를 바꾼다 (NTF-07 관리자 검토 후 수정, CHK-06 표시 값). 보내지 않은 값은 그대로, 빈 문자열은 지운다.
     * 언어와 상관없는 항목 값이라 문구(언어별) 저장과 따로 둔다.
     */
    @Transactional
    public GuideInfoResponse updateInfo(Long adminId, String code, GuideInfoUpdateRequest request) {
        Guide guide = findGuide(code);
        guide.updateSourceAndContact(request.sourceUrl(), request.contact(), adminId, OffsetDateTime.now(clock));
        return toInfoResponse(guide);
    }

    /** 출처 링크·문의 전화 조회. 수정 화면이 지금 값을 채워 보여 주는 데 쓴다 */
    @Transactional(readOnly = true)
    public GuideInfoResponse getInfo(String code) {
        return toInfoResponse(findGuide(code));
    }

    private static GuideInfoResponse toInfoResponse(Guide guide) {
        return new GuideInfoResponse(guide.getCode(), guide.getKind(), guide.getSourceUrl(), guide.getContact(),
                guide.getUpdatedAt());
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
                pageStart, pageEnd, sourcePages,
                t.getPoints() == null ? List.of() : List.copyOf(t.getPoints()), t.getTiming(), t.getContact(),
                t.isReviewed());
    }
}
