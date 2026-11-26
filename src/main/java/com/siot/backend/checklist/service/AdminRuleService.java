package com.siot.backend.checklist.service;

import com.siot.backend.checklist.domain.*;
import com.siot.backend.checklist.dto.RuleCreateRequest;
import com.siot.backend.checklist.dto.RuleResponse;
import com.siot.backend.checklist.dto.RuleUpdateRequest;
import com.siot.backend.checklist.repository.GuideRepository;
import com.siot.backend.checklist.repository.GuideTranslationRepository;
import com.siot.backend.global.exception.BusinessException;
import com.siot.backend.global.exception.ErrorCode;
import com.siot.backend.notification.service.NotificationService;
import com.siot.backend.rag.domain.GuideDocumentReference;
import com.siot.backend.rag.repository.GuideDocumentReferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminRuleService {

    private final GuideRepository guideRepository;
    private final GuideTranslationRepository guideTranslationRepository;
    private final GuideDocumentReferenceRepository guideDocumentReferenceRepository;
    private final NotificationService notificationService;
    private final GuideTranslationGenerator guideTranslationGenerator;
    private final RuleCategoryService ruleCategoryService;
    private final Clock clock;

    /**
     * RUL-09, ADM-06: 관리자 생활수칙 등록
     * RUL-13: 등록 시 다국어(en, zh, vi) 자동 번역 생성
     */
    @Transactional
    public RuleResponse createRule(Long adminId, RuleCreateRequest request) {
        if (guideRepository.findByCode(request.code()).filter(g -> !g.isDeleted()).isPresent()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        Guide guide = Guide.create(
                request.code(),
                "RULE",
                ItemType.INFO,
                request.category(),
                request.sortOrder(),
                null,
                request.faq()
        );
        guide.updateRule(request.category(), request.sortOrder(), request.faq(), request.contact(), request.sourceUrl(), adminId, now);
        guide = guideRepository.save(guide);

        // 한국어 기본 번역본 저장
        GuideTranslation ko = GuideTranslation.create(guide.getId(), "ko");
        ko.update(
                request.title().strip(),
                blankToNull(request.summary()),
                blankToNull(request.content()),
                blankToNull(request.badge()),
                blankToNull(request.place()),
                request.docs() == null ? List.of() : request.docs().stream().map(String::strip).toList(),
                blankToNull(request.fee()),
                blankToNull(request.law()),
                blankToNull(request.penalty()),
                blankToNull(request.sourceName())
        );
        guideTranslationRepository.save(ko);

        // RUL-13: 자동 번역 실행 (en, zh, vi) - 본문과 카테고리명
        guideTranslationGenerator.regenerate(ko);
        ruleCategoryService.ensureTranslations(guide.getCategory());

        return toRuleResponse(guide, ko);
    }

    /**
     * RUL-10, ADM-06: 관리자 생활수칙 수정
     * NTF-07: noticeChanged=true 시 변경 알림 발송 유도
     * RUL-13: 수정 시 다국어 자동 번역 갱신
     */
    @Transactional
    public RuleResponse updateRule(Long adminId, String code, RuleUpdateRequest request) {
        Guide guide = guideRepository.findByCode(code)
                .filter(g -> "RULE".equals(g.getKind()) && !g.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.GUIDE_NOT_FOUND));

        OffsetDateTime now = OffsetDateTime.now(clock);
        guide.updateRule(request.category(), request.sortOrder() != null ? request.sortOrder() : guide.getSortOrder(),
                request.faq() != null ? request.faq() : guide.isFaq(),
                request.contact(), request.sourceUrl(), adminId, now);
        guide.recordEdit(adminId, request.noticeChanged(), now);

        GuideTranslation ko = guideTranslationRepository.findByGuideIdAndLanguage(guide.getId(), "ko")
                .orElseGet(() -> GuideTranslation.create(guide.getId(), "ko"));
        ko.update(
                request.title().strip(),
                blankToNull(request.summary()),
                blankToNull(request.content()),
                blankToNull(request.badge()),
                blankToNull(request.place()),
                request.docs() == null ? List.of() : request.docs().stream().map(String::strip).toList(),
                blankToNull(request.fee()),
                blankToNull(request.law()),
                blankToNull(request.penalty()),
                blankToNull(request.sourceName())
        );
        guideTranslationRepository.save(ko);

        // RUL-13, COM-02: 원문이 바뀌었으므로 번역을 다시 만든다. 새 카테고리면 카테고리명도 번역한다
        guideTranslationGenerator.regenerate(ko);
        ruleCategoryService.ensureTranslations(guide.getCategory());

        // NTF-07: 실제 중요 안내 내용 변경 시 변경 알림 발송
        if (Boolean.TRUE.equals(request.noticeChanged())) {
            notificationService.createNoticeChangedNotifications(guide);
        }

        return toRuleResponse(guide, ko);
    }

    /**
     * RUL-11, ADM-06: 관리자 생활수칙 소프트 삭제
     */
    @Transactional
    public void deleteRule(Long adminId, String code) {
        Guide guide = guideRepository.findByCode(code)
                .filter(g -> "RULE".equals(g.getKind()) && !g.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.GUIDE_NOT_FOUND));

        guide.softDelete(adminId, OffsetDateTime.now(clock));
    }

    private RuleResponse toRuleResponse(Guide guide, GuideTranslation tr) {
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

        return new RuleResponse(
                guide.getCode(),
                guide.getCategory(),
                guide.getCategory(), // 관리자 응답은 한국어
                tr != null ? tr.getTitle() : guide.getCode(),
                tr != null ? tr.getSummary() : null,
                tr != null ? tr.getContent() : null,
                tr != null ? tr.getBadge() : null,
                tr != null ? tr.getPlace() : null,
                tr != null && tr.getDocs() != null ? tr.getDocs() : Collections.emptyList(),
                tr != null ? tr.getFee() : null,
                tr != null ? tr.getLaw() : null,
                tr != null ? tr.getPenalty() : null,
                guide.getContact(),
                guide.getSourceUrl(),
                tr != null ? tr.getSourceName() : null,
                guide.getSortOrder(),
                guide.isFaq(),
                guide.getNoticeChangedAt(),
                pageStart,
                pageEnd,
                sourcePages
        );
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
