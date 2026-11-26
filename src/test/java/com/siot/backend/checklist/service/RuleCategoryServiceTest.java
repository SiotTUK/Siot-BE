package com.siot.backend.checklist.service;

import com.siot.backend.checklist.domain.Guide;
import com.siot.backend.checklist.domain.ItemType;
import com.siot.backend.checklist.domain.RuleCategoryTranslation;
import com.siot.backend.checklist.dto.AdminRuleCategoryResponse;
import com.siot.backend.checklist.dto.RuleCategoryResponse;
import com.siot.backend.checklist.repository.GuideRepository;
import com.siot.backend.checklist.repository.RuleCategoryTranslationRepository;
import com.siot.backend.global.exception.BusinessException;
import com.siot.backend.global.exception.ErrorCode;
import com.siot.backend.translation.Translator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** RUL-12 생활수칙 카테고리, RUL-13 카테고리명 번역 */
@ExtendWith(MockitoExtension.class)
class RuleCategoryServiceTest {

    @Mock
    private GuideRepository guideRepository;
    @Mock
    private RuleCategoryTranslationRepository translationRepository;
    @Mock
    private Translator translator;

    private RuleCategoryService service;

    @BeforeEach
    void setUp() {
        service = new RuleCategoryService(guideRepository, translationRepository, translator,
                Clock.fixed(Instant.parse("2026-11-26T00:00:00Z"), ZoneId.of("Asia/Seoul")));
    }

    private static Guide rule(String code, String category) {
        return Guide.create(code, "RULE", ItemType.INFO, category, 1, null, false);
    }

    @Test
    @DisplayName("카테고리 목록은 요청 언어 이름을 주고, 번역이 없으면 한국어 원문과 translated=false")
    void categoriesInRequestedLanguage() {
        given(guideRepository.findDistinctCategoriesByKind("RULE")).willReturn(List.of("교통질서", "쓰레기 배출"));
        given(translationRepository.findByLanguageAndCategoryIn("en", List.of("교통질서", "쓰레기 배출")))
                .willReturn(List.of(RuleCategoryTranslation.of("쓰레기 배출", "en", "Waste disposal")));

        List<RuleCategoryResponse> result = service.getCategories("en");

        assertThat(result).containsExactly(
                new RuleCategoryResponse("교통질서", "교통질서", false),
                new RuleCategoryResponse("쓰레기 배출", "Waste disposal", true));
    }

    @Test
    @DisplayName("한국어 요청이면 번역을 찾지 않고 원문을 준다")
    void categoriesInKorean() {
        given(guideRepository.findDistinctCategoriesByKind("RULE")).willReturn(List.of("교통질서"));

        assertThat(service.getCategories("ko")).containsExactly(new RuleCategoryResponse("교통질서", "교통질서", true));
        verify(translationRepository, never()).findByLanguageAndCategoryIn(any(), any());
    }

    @Test
    @DisplayName("생활수칙 저장 때 없는 언어만 자동 번역으로 채운다")
    void ensureTranslationsFillsMissingLanguages() {
        given(translationRepository.findByCategory("쓰레기 배출"))
                .willReturn(List.of(RuleCategoryTranslation.of("쓰레기 배출", "en", "Waste (edited)")));
        given(translator.translateAll("쓰레기 배출")).willReturn(Map.of("en", "Waste", "zh", "垃圾投放", "vi", "Đổ rác"));

        service.ensureTranslations("쓰레기 배출");

        ArgumentCaptor<RuleCategoryTranslation> captor = ArgumentCaptor.forClass(RuleCategoryTranslation.class);
        verify(translationRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(RuleCategoryTranslation::getLanguage).containsExactlyInAnyOrder("zh", "vi");
    }

    @Test
    @DisplayName("모든 언어 이름이 있으면 번역을 부르지 않는다")
    void ensureTranslationsSkipsWhenComplete() {
        given(translationRepository.findByCategory("교통질서")).willReturn(List.of(
                RuleCategoryTranslation.of("교통질서", "en", "Traffic"),
                RuleCategoryTranslation.of("교통질서", "zh", "交通秩序"),
                RuleCategoryTranslation.of("교통질서", "vi", "Trật tự giao thông")));

        service.ensureTranslations("교통질서");

        verify(translator, never()).translateAll(any());
    }

    @Test
    @DisplayName("이름을 바꾸면 그 카테고리의 생활수칙을 모두 옮기고 번역을 새 이름으로 다시 만든다")
    void renameMovesRulesAndRetranslates() {
        Guide r1 = rule("RUL-01", "쓰레기");
        Guide r2 = rule("RUL-02", "쓰레기");
        given(guideRepository.findByKindAndCategoryOrderBySortOrderAsc("RULE", "쓰레기")).willReturn(List.of(r1, r2));
        given(translator.translateAll("쓰레기 배출")).willReturn(Map.of("en", "Waste", "zh", "垃圾", "vi", "Rác"));

        AdminRuleCategoryResponse result = service.rename(9L, "쓰레기", "쓰레기 배출");

        assertThat(r1.getCategory()).isEqualTo("쓰레기 배출");
        assertThat(r2.getCategory()).isEqualTo("쓰레기 배출");
        verify(translationRepository).deleteByCategory("쓰레기");
        verify(translationRepository, times(3)).save(any(RuleCategoryTranslation.class));
        assertThat(result.category()).isEqualTo("쓰레기 배출");
    }

    @Test
    @DisplayName("없는 카테고리와 한국어 번역 수정은 거절한다")
    void rejectsUnknownCategoryAndKorean() {
        given(guideRepository.findByKindAndCategoryOrderBySortOrderAsc("RULE", "없음")).willReturn(List.of());

        assertThatThrownBy(() -> service.updateTranslation("없음", "en", "None"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.RULE_CATEGORY_NOT_FOUND);
        assertThatThrownBy(() -> service.updateTranslation("교통질서", "ko", "교통"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
    }
}
