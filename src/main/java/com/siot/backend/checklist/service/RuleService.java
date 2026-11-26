package com.siot.backend.checklist.service;

import com.siot.backend.checklist.domain.Guide;
import com.siot.backend.checklist.domain.GuideTranslation;
import com.siot.backend.checklist.dto.RuleCategoryResponse;
import com.siot.backend.checklist.dto.RuleResponse;
import com.siot.backend.checklist.repository.GuideRepository;
import com.siot.backend.checklist.repository.GuideTranslationRepository;
import com.siot.backend.global.exception.BusinessException;
import com.siot.backend.global.exception.ErrorCode;
import com.siot.backend.rag.domain.GuideDocumentReference;
import com.siot.backend.rag.repository.GuideDocumentReferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * 생활수칙(RULE) 및 자주 묻는 질문(FAQ TOP5) 조회 서비스 (RUL-01 ~ RUL-13)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RuleService {

    private final GuideRepository guideRepository;
    private final GuideTranslationRepository guideTranslationRepository;
    private final GuideDocumentReferenceRepository guideDocumentReferenceRepository;
    private final RuleCategoryService ruleCategoryService;

    public List<RuleResponse> getFaqTop5(String language) {
        String targetLang = language != null && !language.isBlank() ? language : "ko";
        List<Guide> guides = guideRepository.findTop5ByKindAndFaqTrueAndDeletedAtIsNullOrderBySortOrderAsc("RULE");
        return guides.stream().map(g -> toRuleResponse(g, targetLang)).toList();
    }

    public List<RuleResponse> getRules(String category, String language) {
        String targetLang = language != null && !language.isBlank() ? language : "ko";
        List<Guide> guides;
        if (category == null || category.isBlank() || "전체".equals(category)) {
            guides = guideRepository.findByKindOrderBySortOrderAsc("RULE");
        } else {
            guides = guideRepository.findByKindAndCategoryOrderBySortOrderAsc("RULE", category);
        }
        return guides.stream()
                .filter(g -> !g.isDeleted())
                .map(g -> toRuleResponse(g, targetLang))
                .toList();
    }

    /** RUL-12·RUL-13: 카테고리 목록과 요청 언어의 이름 */
    public List<RuleCategoryResponse> getCategories(String language) {
        return ruleCategoryService.getCategories(language);
    }

    public RuleResponse getRule(String code, String language) {
        String targetLang = language != null && !language.isBlank() ? language : "ko";
        Guide guide = guideRepository.findByCode(code)
                .filter(g -> "RULE".equals(g.getKind()) && !g.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.GUIDE_NOT_FOUND));

        return toRuleResponse(guide, targetLang);
    }

    private RuleResponse toRuleResponse(Guide guide, String language) {
        GuideTranslation tr = guideTranslationRepository.findByGuideIdAndLanguage(guide.getId(), language)
                .or(() -> guideTranslationRepository.findByGuideIdAndLanguage(guide.getId(), "ko"))
                .orElse(null);

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
                ruleCategoryService.nameOf(guide.getCategory(), language),
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
}
