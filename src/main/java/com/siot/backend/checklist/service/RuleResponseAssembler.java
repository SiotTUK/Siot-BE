package com.siot.backend.checklist.service;

import com.siot.backend.checklist.domain.Guide;
import com.siot.backend.checklist.domain.GuideTranslation;
import com.siot.backend.checklist.dto.RuleResponse;
import com.siot.backend.checklist.dto.RuleSourceResponse;
import com.siot.backend.checklist.repository.GuideSourceRepository;
import com.siot.backend.checklist.repository.GuideTranslationRepository;
import com.siot.backend.global.common.SupportedLanguages;
import com.siot.backend.rag.domain.GuideDocumentReference;
import com.siot.backend.rag.repository.GuideDocumentReferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** 생활수칙 응답을 만든다 (회원 RuleService, 관리자 AdminRuleService 공통). 요청 언어 문구가 없으면 한국어 원문 */
@Component
@RequiredArgsConstructor
public class RuleResponseAssembler {

    private final GuideTranslationRepository guideTranslationRepository;
    private final GuideDocumentReferenceRepository guideDocumentReferenceRepository;
    private final GuideSourceRepository guideSourceRepository;
    private final RuleCategoryService ruleCategoryService;

    /** 요청 언어 문구로. 없으면 한국어 */
    public RuleResponse assemble(Guide guide, String language) {
        String lang = SupportedLanguages.orDefault(language);
        GuideTranslation tr = guideTranslationRepository.findByGuideIdAndLanguage(guide.getId(), lang).orElse(null);
        boolean translated = tr != null;
        if (tr == null) {
            tr = guideTranslationRepository.findByGuideIdAndLanguage(guide.getId(), SupportedLanguages.DEFAULT).orElse(null);
        }
        return assemble(guide, tr, ruleCategoryService.nameOf(guide.getCategory(), lang), translated);
    }

    /** 관리자: 이미 읽은 한국어 문구로 (카테고리명도 한국어) */
    public RuleResponse assembleKorean(Guide guide, GuideTranslation ko) {
        return assemble(guide, ko, guide.getCategory(), true);
    }

    private RuleResponse assemble(Guide guide, GuideTranslation tr, String categoryName, boolean translated) {
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
        List<RuleSourceResponse> sources = guideSourceRepository.findByGuideIdOrderBySortOrderAsc(guide.getId()).stream()
                .map(source -> new RuleSourceResponse(source.getName(), source.getUrl()))
                .toList();
        if (sources.isEmpty() && guide.getSourceUrl() != null) {
            // 출처 목록이 없는 예전 항목: 출처 링크 하나
            sources = List.of(new RuleSourceResponse(tr != null && tr.getSourceName() != null ? tr.getSourceName()
                    : guide.getSourceUrl(), guide.getSourceUrl()));
        }
        String contact = tr != null && tr.getContact() != null ? tr.getContact() : guide.getContact();

        return new RuleResponse(
                guide.getCode(),
                guide.getCategory(),
                categoryName,
                tr != null ? tr.getTitle() : guide.getCode(),
                tr != null ? tr.getSummary() : null,
                tr != null ? tr.getContent() : null,
                tr != null ? tr.getBadge() : null,
                tr != null ? tr.getPlace() : null,
                tr != null && tr.getDocs() != null ? tr.getDocs() : List.of(),
                tr != null ? tr.getFee() : null,
                tr != null ? tr.getLaw() : null,
                tr != null ? tr.getPenalty() : null,
                contact,
                guide.getSourceUrl(),
                tr != null ? tr.getSourceName() : null,
                guide.getSortOrder(),
                guide.isFaq(),
                guide.getNoticeChangedAt(),
                pageStart,
                pageEnd,
                sourcePages,
                guide.getId(),
                ruleCategoryService.keyOf(guide.getCategory()),
                tr != null && tr.getPoints() != null ? tr.getPoints() : List.of(),
                tr != null ? tr.getTiming() : null,
                sources,
                guide.getCheckedOn(),
                translated
        );
    }
}
