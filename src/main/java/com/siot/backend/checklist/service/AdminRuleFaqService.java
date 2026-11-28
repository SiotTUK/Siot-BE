package com.siot.backend.checklist.service;

import com.siot.backend.checklist.domain.Guide;
import com.siot.backend.checklist.domain.GuideTranslation;
import com.siot.backend.checklist.domain.RuleFaq;
import com.siot.backend.checklist.domain.RuleFaqTranslation;
import com.siot.backend.checklist.dto.AdminRuleFaqResponse;
import com.siot.backend.checklist.dto.RuleFaqRequest;
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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 관리자: 생활수칙 자주 묻는 질문 관리 (RUL-04, ADM-06). 질문·답변은 관리자가 언어별로 직접 쓴다 */
@Service
@RequiredArgsConstructor
public class AdminRuleFaqService {

    private final RuleFaqRepository faqRepository;
    private final RuleFaqTranslationRepository translationRepository;
    private final GuideRepository guideRepository;
    private final GuideTranslationRepository guideTranslationRepository;

    /** 순서대로. 회원 화면(GET /api/rules/faq)이 덧붙이는 is_faq 생활수칙은 넣지 않는다 */
    @Transactional(readOnly = true)
    public List<AdminRuleFaqResponse> getFaqs() {
        List<RuleFaq> faqs = faqRepository.findAllByOrderBySortOrderAscIdAsc();
        if (faqs.isEmpty()) {
            return List.of();
        }
        Map<Long, List<RuleFaqTranslation>> texts = translationRepository
                .findByFaqIdIn(faqs.stream().map(RuleFaq::getId).toList()).stream()
                .collect(Collectors.groupingBy(RuleFaqTranslation::getFaqId));
        Map<Long, Guide> guides = guidesById(faqs.stream().map(RuleFaq::getGuideId).filter(Objects::nonNull).toList());
        return faqs.stream()
                .map(faq -> toResponse(faq, texts.getOrDefault(faq.getId(), List.of()), guides.get(faq.getGuideId())))
                .toList();
    }

    @Transactional
    public AdminRuleFaqResponse create(RuleFaqRequest request) {
        Guide guide = linkedRule(request.ruleCode());
        Map<String, RuleFaqRequest.Text> texts = checkTranslations(request.translations());
        RuleFaq faq = faqRepository.save(RuleFaq.create(guide == null ? null : guide.getId(), request.sortOrder()));
        List<RuleFaqTranslation> saved = translationRepository.saveAll(texts.entrySet().stream()
                .map(e -> RuleFaqTranslation.create(faq.getId(), e.getKey(), e.getValue().question().strip(),
                        e.getValue().answer().strip()))
                .toList());
        return toResponse(faq, saved, guide);
    }

    /** 보낸 값으로 통째로 바꾼다. 보내지 않은 언어는 지운다 */
    @Transactional
    public AdminRuleFaqResponse update(Long faqId, RuleFaqRequest request) {
        RuleFaq faq = findFaq(faqId);
        Guide guide = linkedRule(request.ruleCode());
        Map<String, RuleFaqRequest.Text> texts = checkTranslations(request.translations());
        faq.update(guide == null ? null : guide.getId(), request.sortOrder());

        Map<String, RuleFaqTranslation> current = translationRepository.findByFaqIdIn(List.of(faqId)).stream()
                .collect(Collectors.toMap(RuleFaqTranslation::getLanguage, Function.identity()));
        current.forEach((language, translation) -> {
            if (!texts.containsKey(language)) {
                translationRepository.delete(translation);
            }
        });
        List<RuleFaqTranslation> saved = texts.entrySet().stream().map(e -> {
            String question = e.getValue().question().strip();
            String answer = e.getValue().answer().strip();
            RuleFaqTranslation translation = current.get(e.getKey());
            if (translation == null) {
                return translationRepository.save(RuleFaqTranslation.create(faqId, e.getKey(), question, answer));
            }
            translation.update(question, answer);
            return translation;
        }).toList();
        return toResponse(faq, saved, guide);
    }

    /** 질문·답변도 함께 지운다 (FK ON DELETE CASCADE) */
    @Transactional
    public void delete(Long faqId) {
        faqRepository.delete(findFaq(faqId));
    }

    private RuleFaq findFaq(Long faqId) {
        return faqRepository.findById(faqId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RULE_FAQ_NOT_FOUND));
    }

    /** 비어 있으면 연결 없음. 지우지 않은 생활수칙만 연결할 수 있다 */
    private Guide linkedRule(String ruleCode) {
        if (ruleCode == null || ruleCode.isBlank()) {
            return null;
        }
        return guideRepository.findByCode(ruleCode.strip())
                .filter(g -> "RULE".equals(g.getKind()) && !g.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.GUIDE_NOT_FOUND));
    }

    /** 언어는 ko·en·zh·vi 만, ko 는 필수 */
    private static Map<String, RuleFaqRequest.Text> checkTranslations(Map<String, RuleFaqRequest.Text> texts) {
        if (!texts.containsKey(SupportedLanguages.DEFAULT)
                || !SupportedLanguages.ALL.containsAll(texts.keySet())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "질문·답변은 ko(필수), en, zh, vi 로 보내 주세요.");
        }
        return texts;
    }

    private Map<Long, Guide> guidesById(Collection<Long> ids) {
        return ids.isEmpty() ? Map.of() : guideRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Guide::getId, Function.identity()));
    }

    private AdminRuleFaqResponse toResponse(RuleFaq faq, List<RuleFaqTranslation> texts, Guide guide) {
        Map<String, RuleFaqRequest.Text> translations = new TreeMap<>();
        texts.forEach(t -> translations.put(t.getLanguage(), new RuleFaqRequest.Text(t.getQuestion(), t.getAnswer())));
        String ruleTitle = guide == null ? null : guideTranslationRepository
                .findByGuideIdAndLanguage(guide.getId(), SupportedLanguages.DEFAULT)
                .map(GuideTranslation::getTitle).orElse(guide.getCode());
        return new AdminRuleFaqResponse(faq.getId(), faq.getSortOrder(), guide == null ? null : guide.getCode(),
                ruleTitle, guide != null && guide.isDeleted(), translations);
    }
}
