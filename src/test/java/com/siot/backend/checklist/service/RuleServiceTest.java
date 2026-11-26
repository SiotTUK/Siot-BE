package com.siot.backend.checklist.service;

import com.siot.backend.checklist.domain.Guide;
import com.siot.backend.checklist.domain.GuideTranslation;
import com.siot.backend.checklist.domain.ItemType;
import com.siot.backend.checklist.domain.RuleFaq;
import com.siot.backend.checklist.domain.RuleFaqTranslation;
import com.siot.backend.checklist.dto.RuleFaqResponse;
import com.siot.backend.checklist.repository.GuideRepository;
import com.siot.backend.checklist.repository.GuideTranslationRepository;
import com.siot.backend.checklist.repository.RuleFaqRepository;
import com.siot.backend.checklist.repository.RuleFaqTranslationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RuleServiceTest {

    @Mock
    private GuideRepository guideRepository;
    @Mock
    private GuideTranslationRepository guideTranslationRepository;
    @Mock
    private RuleCategoryService ruleCategoryService;
    @Mock
    private RuleResponseAssembler assembler;
    @Mock
    private RuleFaqRepository faqRepository;
    @Mock
    private RuleFaqTranslationRepository faqTranslationRepository;

    @InjectMocks
    private RuleService ruleService;

    @Test
    @DisplayName("#14 FAQ: 요청 언어 질문·답변(없으면 한국어)과 자세히 볼 생활수칙 코드·제목, 순서대로")
    void faqsInRequestedLanguageWithLinkedRule() {
        Guide rule = Guide.create("waste-food", "RULE", ItemType.INFO, "쓰레기", 102, null, true);
        ReflectionTestUtils.setField(rule, "id", 7L);
        RuleFaq first = faq(1L, 7L, 1);
        RuleFaq second = faq(2L, null, 2);
        given(faqRepository.findAllByOrderBySortOrderAscIdAsc()).willReturn(List.of(first, second));
        given(faqTranslationRepository.findByFaqIdIn(any())).willReturn(List.of(
                text(1L, "ko", "음식물 쓰레기는?", "노란 봉투"),
                text(1L, "en", "Food waste?", "Yellow bag"),
                text(2L, "ko", "주민세는?", "8월")));
        given(guideRepository.findAllById(List.of(7L))).willReturn(List.of(rule));
        given(guideTranslationRepository.findByGuideIdAndLanguage(7L, "en"))
                .willReturn(Optional.of(GuideTranslation.create(7L, "en", "Food waste", null, null, null, null, null,
                        null, null, null, null)));

        List<RuleFaqResponse> faqs = ruleService.getFaqs("en");

        assertThat(faqs).containsExactly(
                new RuleFaqResponse(1, "Food waste?", "Yellow bag", "waste-food", "Food waste", "en"),
                new RuleFaqResponse(2, "주민세는?", "8월", null, null, "ko"));
    }

    private static RuleFaq faq(Long id, Long guideId, int sortOrder) {
        RuleFaq faq = org.springframework.beans.BeanUtils.instantiateClass(RuleFaq.class);
        ReflectionTestUtils.setField(faq, "id", id);
        ReflectionTestUtils.setField(faq, "guideId", guideId);
        ReflectionTestUtils.setField(faq, "sortOrder", sortOrder);
        return faq;
    }

    private static RuleFaqTranslation text(Long faqId, String language, String question, String answer) {
        RuleFaqTranslation text = org.springframework.beans.BeanUtils.instantiateClass(RuleFaqTranslation.class);
        ReflectionTestUtils.setField(text, "faqId", faqId);
        ReflectionTestUtils.setField(text, "language", language);
        ReflectionTestUtils.setField(text, "question", question);
        ReflectionTestUtils.setField(text, "answer", answer);
        return text;
    }
}
