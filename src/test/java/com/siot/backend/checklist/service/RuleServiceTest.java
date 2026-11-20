package com.siot.backend.checklist.service;

import com.siot.backend.checklist.domain.Guide;
import com.siot.backend.checklist.domain.GuideTranslation;
import com.siot.backend.checklist.domain.ItemType;
import com.siot.backend.checklist.dto.RuleResponse;
import com.siot.backend.checklist.repository.GuideRepository;
import com.siot.backend.checklist.repository.GuideTranslationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RuleServiceTest {

    @Mock
    private GuideRepository guideRepository;
    @Mock
    private GuideTranslationRepository guideTranslationRepository;

    @InjectMocks
    private RuleService ruleService;

    @Test
    @DisplayName("FAQ TOP 5 생활수칙 조회 검증")
    void getFaqTop5_success() {
        // given
        Guide g1 = Guide.create("RUL-01", "RULE", ItemType.INFO, "쓰레기 배출", 1, null, true);
        Guide g2 = Guide.create("RUL-02", "RULE", ItemType.INFO, "교통 질서", 2, null, true);

        given(guideRepository.findTop5ByKindAndFaqTrueAndDeletedAtIsNullOrderBySortOrderAsc("RULE"))
                .willReturn(List.of(g1, g2));

        GuideTranslation t1 = GuideTranslation.create(g1.getId(), "ko", "종량제 봉투 배출", "요약", "본문", "기한", "장소", Collections.emptyList(), "수수료", "법령", "과태료", "문의");
        GuideTranslation t2 = GuideTranslation.create(g2.getId(), "ko", "무단 횡단 금지", "요약", "본문", "기한", "장소", Collections.emptyList(), "수수료", "법령", "과태료", "문의");

        given(guideTranslationRepository.findByGuideIdAndLanguage(g1.getId(), "ko"))
                .willReturn(Optional.of(t1));
        given(guideTranslationRepository.findByGuideIdAndLanguage(g2.getId(), "ko"))
                .willReturn(Optional.of(t2));

        // when
        List<RuleResponse> faqList = ruleService.getFaqTop5("ko");

        // then
        assertThat(faqList).hasSize(2);
        assertThat(faqList.get(0).code()).isEqualTo("RUL-01");
        assertThat(faqList.get(0).faq()).isTrue();
        assertThat(faqList.get(1).code()).isEqualTo("RUL-02");
    }
}
