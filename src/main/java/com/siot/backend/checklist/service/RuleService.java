package com.siot.backend.checklist.service;

import com.siot.backend.checklist.domain.Guide;
import com.siot.backend.checklist.domain.GuideTranslation;
import com.siot.backend.checklist.domain.RuleFaq;
import com.siot.backend.checklist.domain.RuleFaqTranslation;
import com.siot.backend.checklist.dto.RuleCategoryResponse;
import com.siot.backend.checklist.dto.RuleFaqResponse;
import com.siot.backend.checklist.dto.RuleResponse;
import com.siot.backend.checklist.repository.GuideRepository;
import com.siot.backend.checklist.repository.GuideTranslationRepository;
import com.siot.backend.checklist.repository.RuleFaqRepository;
import com.siot.backend.checklist.repository.RuleFaqTranslationRepository;
import com.siot.backend.global.common.SupportedLanguages;
import com.siot.backend.global.exception.BusinessException;
import com.siot.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 생활수칙(RULE) 및 자주 묻는 질문(FAQ) 조회 서비스 (RUL-01 ~ RUL-13).
 * #14 FE 구조: 슬러그 코드, 카테고리 키·순서, 문장 목록, 시기, 출처 목록, 확인일, FAQ 질문·답변.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RuleService {

    private final GuideRepository guideRepository;
    private final GuideTranslationRepository guideTranslationRepository;
    private final RuleCategoryService ruleCategoryService;
    private final RuleResponseAssembler assembler;
    private final RuleFaqRepository faqRepository;
    private final RuleFaqTranslationRepository faqTranslationRepository;

    /** RUL-04 자주 묻는 질문: 질문·답변(요청 언어, 없으면 한국어)과 자세히 볼 생활수칙, 순서대로 */
    public List<RuleFaqResponse> getFaqs(String language) {
        String lang = SupportedLanguages.orDefault(language);
        List<RuleFaq> faqs = faqRepository.findAllByOrderBySortOrderAscIdAsc();
        List<RuleFaqResponse> written = faqs.isEmpty() ? List.of() : writtenFaqs(faqs, lang);
        // 관리자가 FAQ 로 표시(is_faq)했지만 질문을 따로 쓰지 않은 생활수칙: 제목을 질문으로, 요약을 답변으로 뒤에 붙인다
        java.util.Set<Long> linked = faqs.stream().map(RuleFaq::getGuideId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        List<RuleFaqResponse> result = new java.util.ArrayList<>(written);
        for (Guide guide : guideRepository.findByKindOrderBySortOrderAsc("RULE")) {
            if (!guide.isFaq() || guide.isDeleted() || linked.contains(guide.getId())) {
                continue;
            }
            GuideTranslation tr = guideTranslationRepository.findByGuideIdAndLanguage(guide.getId(), lang)
                    .or(() -> guideTranslationRepository.findByGuideIdAndLanguage(guide.getId(), SupportedLanguages.DEFAULT))
                    .orElse(null);
            if (tr == null) {
                continue;
            }
            result.add(new RuleFaqResponse(result.size() + 1, tr.getTitle(), tr.getSummary(), guide.getCode(),
                    tr.getTitle(), tr.getLanguage()));
        }
        return result;
    }

    private List<RuleFaqResponse> writtenFaqs(List<RuleFaq> faqs, String lang) {
        Map<Long, Map<String, RuleFaqTranslation>> texts = faqTranslationRepository
                .findByFaqIdIn(faqs.stream().map(RuleFaq::getId).toList()).stream()
                .collect(Collectors.groupingBy(RuleFaqTranslation::getFaqId,
                        Collectors.toMap(RuleFaqTranslation::getLanguage, Function.identity())));
        List<Long> guideIds = faqs.stream().map(RuleFaq::getGuideId).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, Guide> guides = guideRepository.findAllById(guideIds).stream()
                .filter(g -> "RULE".equals(g.getKind()) && !g.isDeleted())
                .collect(Collectors.toMap(Guide::getId, Function.identity()));

        return faqs.stream()
                .map(faq -> {
                    Map<String, RuleFaqTranslation> byLang = texts.getOrDefault(faq.getId(), Map.of());
                    RuleFaqTranslation text = byLang.containsKey(lang) ? byLang.get(lang) : byLang.get(SupportedLanguages.DEFAULT);
                    if (text == null) {
                        return null;
                    }
                    Guide guide = faq.getGuideId() == null ? null : guides.get(faq.getGuideId());
                    return new RuleFaqResponse(faq.getSortOrder(), text.getQuestion(), text.getAnswer(),
                            guide == null ? null : guide.getCode(), guide == null ? null : title(guide, lang),
                            text.getLanguage());
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * @param category 한국어 카테고리 이름 또는 FE 키(waste 등). 비우거나 "전체"면 전체
     */
    public List<RuleResponse> getRules(String category, String language) {
        List<Guide> guides;
        if (category == null || category.isBlank() || "전체".equals(category)) {
            guides = guideRepository.findByKindOrderBySortOrderAsc("RULE");
        } else {
            guides = guideRepository.findByKindAndCategoryOrderBySortOrderAsc("RULE",
                    ruleCategoryService.resolve(category.strip()));
        }
        return guides.stream()
                .filter(g -> !g.isDeleted())
                .map(g -> assembler.assemble(g, language))
                .toList();
    }

    /** RUL-12·RUL-13: 카테고리 목록과 요청 언어의 이름 */
    public List<RuleCategoryResponse> getCategories(String language) {
        return ruleCategoryService.getCategories(language);
    }

    public RuleResponse getRule(String code, String language) {
        Guide guide = guideRepository.findByCode(code)
                .filter(g -> "RULE".equals(g.getKind()) && !g.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.GUIDE_NOT_FOUND));
        return assembler.assemble(guide, language);
    }

    private String title(Guide guide, String language) {
        return guideTranslationRepository.findByGuideIdAndLanguage(guide.getId(), language)
                .or(() -> guideTranslationRepository.findByGuideIdAndLanguage(guide.getId(), SupportedLanguages.DEFAULT))
                .map(GuideTranslation::getTitle)
                .orElse(guide.getCode());
    }
}
