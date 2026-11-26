package com.siot.backend.checklist.service;

import com.siot.backend.checklist.domain.Guide;
import com.siot.backend.checklist.domain.RuleCategory;
import com.siot.backend.checklist.domain.RuleCategoryTranslation;
import com.siot.backend.checklist.domain.RuleCategoryTranslationId;
import com.siot.backend.checklist.dto.AdminRuleCategoryResponse;
import com.siot.backend.checklist.dto.RuleCategoryResponse;
import com.siot.backend.checklist.repository.GuideRepository;
import com.siot.backend.checklist.repository.RuleCategoryRepository;
import com.siot.backend.checklist.repository.RuleCategoryTranslationRepository;
import com.siot.backend.global.common.SupportedLanguages;
import com.siot.backend.global.exception.BusinessException;
import com.siot.backend.global.exception.ErrorCode;
import com.siot.backend.translation.Translator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 생활수칙 카테고리 (RUL-12) 와 카테고리명 번역 (RUL-13).
 * 카테고리는 guide.category 의 한국어 이름이고, en/zh/vi 이름은 rule_category_translation 에 둔다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RuleCategoryService {

    private static final String RULE = "RULE";

    private final GuideRepository guideRepository;
    private final RuleCategoryTranslationRepository translationRepository;
    private final Translator translator;
    private final RuleCategoryRepository categoryRepository;
    private final Clock clock;

    /** 사용자: 카테고리 목록과 요청 언어의 이름. #14 피그마 M06 순서(rule_category.sort_order), 그다음 이름순 */
    public List<RuleCategoryResponse> getCategories(String language) {
        String lang = SupportedLanguages.orDefault(language);
        List<String> categories = guideRepository.findDistinctCategoriesByKind(RULE);
        Map<String, String> names = SupportedLanguages.DEFAULT.equals(lang) || categories.isEmpty() ? Map.of()
                : translationRepository.findByLanguageAndCategoryIn(lang, categories).stream()
                .collect(Collectors.toMap(RuleCategoryTranslation::getCategory, RuleCategoryTranslation::getName));
        Map<String, RuleCategory> meta = categories.isEmpty() ? Map.of()
                : categoryRepository.findAllById(categories).stream()
                .collect(Collectors.toMap(RuleCategory::getCategory, c -> c));
        return categories.stream()
                .map(c -> {
                    RuleCategory m = meta.get(c);
                    String key = m == null ? null : m.getCategoryKey();
                    int order = m == null ? RuleCategory.DEFAULT_SORT_ORDER : m.getSortOrder();
                    return SupportedLanguages.DEFAULT.equals(lang)
                            ? new RuleCategoryResponse(c, c, true, key, order)
                            : new RuleCategoryResponse(c, names.getOrDefault(c, c), names.containsKey(c), key, order);
                })
                .sorted(java.util.Comparator.comparingInt(RuleCategoryResponse::sortOrder)
                        .thenComparing(RuleCategoryResponse::category))
                .toList();
    }

    /** #14 FE 키 (없으면 null) */
    public String keyOf(String category) {
        if (category == null) {
            return null;
        }
        return categoryRepository.findById(category).map(RuleCategory::getCategoryKey).orElse(null);
    }

    /** #14 목록 필터: FE 키(waste 등)를 보내면 한국어 카테고리 이름으로 바꾼다. 키가 아니면 그대로 */
    public String resolve(String categoryOrKey) {
        if (categoryOrKey == null) {
            return null;
        }
        return categoryRepository.findByCategoryKey(categoryOrKey).map(RuleCategory::getCategory).orElse(categoryOrKey);
    }

    /** 요청 언어의 카테고리명. 번역이 없으면 한국어 원문 */
    public String nameOf(String category, String language) {
        String lang = SupportedLanguages.orDefault(language);
        if (category == null || SupportedLanguages.DEFAULT.equals(lang)) {
            return category;
        }
        return translationRepository.findById(new RuleCategoryTranslationId(category, lang))
                .map(RuleCategoryTranslation::getName)
                .orElse(category);
    }

    /** 생활수칙을 저장할 때: 이 카테고리에 없는 언어의 이름을 자동 번역으로 채운다 (실패하면 다음 저장 때 다시) */
    @Transactional
    public void ensureTranslations(String category) {
        if (category == null || category.isBlank()) {
            return;
        }
        Set<String> existing = translationRepository.findByCategory(category).stream()
                .map(RuleCategoryTranslation::getLanguage)
                .collect(Collectors.toSet());
        if (existing.containsAll(Translator.TARGET_LANGUAGES)) {
            return;
        }
        Map<String, String> translated;
        try {
            translated = translator.translateAll(category);
        } catch (Exception e) {
            log.warn("Rule category translation failed for '{}': {}", category, e.getMessage());
            return;
        }
        for (String lang : Translator.TARGET_LANGUAGES) {
            String name = translated.get(lang);
            if (!existing.contains(lang) && name != null && !name.isBlank()) {
                translationRepository.save(RuleCategoryTranslation.of(category, lang, truncate(name.strip())));
            }
        }
    }

    /** 관리자: 카테고리별 생활수칙 수와 언어별 이름 */
    public List<AdminRuleCategoryResponse> getAdminCategories() {
        List<String> categories = guideRepository.findDistinctCategoriesByKind(RULE);
        if (categories.isEmpty()) {
            return List.of();
        }
        Map<String, Map<String, String>> translations = translationRepository.findByCategoryIn(categories).stream()
                .collect(Collectors.groupingBy(RuleCategoryTranslation::getCategory,
                        Collectors.toMap(RuleCategoryTranslation::getLanguage, RuleCategoryTranslation::getName,
                                (a, b) -> a, TreeMap::new)));
        return categories.stream()
                .map(c -> new AdminRuleCategoryResponse(c, activeRules(c).size(),
                        translations.getOrDefault(c, Map.of())))
                .toList();
    }

    /** 관리자: 한 언어의 카테고리명을 고친다 */
    @Transactional
    public AdminRuleCategoryResponse updateTranslation(String category, String language, String name) {
        if (!Translator.TARGET_LANGUAGES.contains(language)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT); // ko 는 이름 바꾸기로 고친다
        }
        requireExisting(category);
        String value = name.strip();
        translationRepository.findById(new RuleCategoryTranslationId(category, language))
                .ifPresentOrElse(t -> t.rename(value),
                        () -> translationRepository.save(RuleCategoryTranslation.of(category, language, value)));
        return adminResponse(category);
    }

    /**
     * 관리자: 카테고리 이름(한국어)을 바꾼다. 그 카테고리의 생활수칙을 모두 옮기고, 번역은 새 이름으로 다시 만든다.
     * 새 이름이 이미 있는 카테고리면 그 카테고리로 합친다.
     */
    @Transactional
    public AdminRuleCategoryResponse rename(Long adminId, String category, String newName) {
        String target = newName.strip();
        List<Guide> rules = requireExisting(category);
        if (target.equals(category)) {
            return adminResponse(category);
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        // 삭제한 생활수칙도 옮긴다 (옛 이름이 목록에 다시 나오지 않게)
        for (Guide guide : guideRepository.findByKindAndCategoryOrderBySortOrderAsc(RULE, category)) {
            guide.updateRule(target, guide.getSortOrder(), guide.isFaq(), null, null, adminId, now);
        }
        translationRepository.deleteByCategory(category);
        // #14 FE 키·순서도 새 이름으로 옮긴다 (새 이름에 이미 있으면 그쪽을 쓴다)
        categoryRepository.findById(category).ifPresent(old -> {
            categoryRepository.delete(old);
            categoryRepository.flush();
            if (!categoryRepository.existsById(target)) {
                categoryRepository.save(RuleCategory.of(target, old.getCategoryKey(), old.getSortOrder()));
            }
        });
        ensureTranslations(target);
        log.info("Rule category renamed: '{}' -> '{}' ({} rules)", category, target, rules.size());
        return adminResponse(target);
    }

    private List<Guide> requireExisting(String category) {
        List<Guide> rules = activeRules(category);
        if (rules.isEmpty()) {
            throw new BusinessException(ErrorCode.RULE_CATEGORY_NOT_FOUND);
        }
        return rules;
    }

    private List<Guide> activeRules(String category) {
        return guideRepository.findByKindAndCategoryOrderBySortOrderAsc(RULE, category).stream()
                .filter(g -> !g.isDeleted())
                .toList();
    }

    private AdminRuleCategoryResponse adminResponse(String category) {
        Map<String, String> translations = translationRepository.findByCategory(category).stream()
                .collect(Collectors.toMap(RuleCategoryTranslation::getLanguage, RuleCategoryTranslation::getName,
                        (a, b) -> a, TreeMap::new));
        return new AdminRuleCategoryResponse(category, activeRules(category).size(), translations);
    }

    private static String truncate(String value) {
        return value.length() <= 100 ? value : value.substring(0, 100);
    }
}
