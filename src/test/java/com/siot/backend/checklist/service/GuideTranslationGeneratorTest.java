package com.siot.backend.checklist.service;

import com.siot.backend.checklist.domain.GuideTranslation;
import com.siot.backend.checklist.repository.GuideTranslationRepository;
import com.siot.backend.rag.ai.AiUnavailableException;
import com.siot.backend.translation.Translator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** COM-02: 한국어 원문으로 en/zh/vi 번역을 다시 만든다 */
@ExtendWith(MockitoExtension.class)
class GuideTranslationGeneratorTest {

    @Mock
    private GuideTranslationRepository guideTranslationRepository;
    @Mock
    private Translator translator;

    @InjectMocks
    private GuideTranslationGenerator generator;

    private static GuideTranslation korean() {
        return GuideTranslation.create(7L, "ko", "외국인등록", "요약", null, null, null,
                List.of("여권"), null, null, null, null);
    }

    @Test
    @DisplayName("모든 칸과 준비물을 번역해 언어별로 저장한다")
    void regeneratesAllLanguagesIncludingDocs() {
        given(translator.translateAll(anyString())).willAnswer(inv -> {
            String text = inv.getArgument(0);
            return Map.of("en", text + "-en", "zh", text + "-zh", "vi", text + "-vi");
        });
        given(guideTranslationRepository.findByGuideIdAndLanguage(any(), anyString())).willReturn(Optional.empty());

        generator.regenerate(korean());

        ArgumentCaptor<GuideTranslation> captor = ArgumentCaptor.forClass(GuideTranslation.class);
        verify(guideTranslationRepository, times(3)).save(captor.capture());
        GuideTranslation en = captor.getAllValues().stream().filter(t -> "en".equals(t.getLanguage())).findFirst().orElseThrow();
        assertThat(en.getTitle()).isEqualTo("외국인등록-en");
        assertThat(en.getSummary()).isEqualTo("요약-en");
        assertThat(en.getDocs()).containsExactly("여권-en");
        assertThat(en.getContent()).isNull();
    }

    @Test
    @DisplayName("번역에 실패하면 한국어를 번역본으로 저장하지 않는다 (화면은 원문 + 번역 준비 중)")
    void translationFailureSavesNothing() {
        given(translator.translateAll(anyString())).willThrow(new AiUnavailableException("down"));

        generator.regenerate(korean());

        verify(guideTranslationRepository, never()).save(any());
    }

    @Test
    @DisplayName("제목 외 칸만 번역에 실패하면 그 칸만 비워 둔다")
    void partialFailureLeavesFieldEmpty() {
        given(translator.translateAll("외국인등록")).willReturn(Map.of("en", "Registration", "zh", "登录", "vi", "Đăng ký"));
        given(translator.translateAll("요약")).willThrow(new AiUnavailableException("down"));
        given(translator.translateAll("여권")).willReturn(Map.of("en", "Passport", "zh", "护照", "vi", "Hộ chiếu"));
        given(guideTranslationRepository.findByGuideIdAndLanguage(any(), anyString())).willReturn(Optional.empty());

        generator.regenerate(korean());

        ArgumentCaptor<GuideTranslation> captor = ArgumentCaptor.forClass(GuideTranslation.class);
        verify(guideTranslationRepository, times(3)).save(captor.capture());
        GuideTranslation en = captor.getAllValues().stream().filter(t -> "en".equals(t.getLanguage())).findFirst().orElseThrow();
        assertThat(en.getTitle()).isEqualTo("Registration");
        assertThat(en.getSummary()).isNull();
        assertThat(en.getDocs()).containsExactly("Passport");
    }

    @Test
    @DisplayName("번역문이 DB 칸보다 길면 자른다 (배지 100자, 제목 255자) — 커밋 때 요청 전체가 실패하지 않게")
    void truncatesTranslationsToColumnLength() {
        GuideTranslation ko = GuideTranslation.create(7L, "ko", "제목", null, null, "입국 후 90일 이내", null,
                List.of(), null, null, null, null);
        given(translator.translateAll(anyString())).willAnswer(inv -> {
            String longText = "x".repeat(300);
            return Map.of("en", longText, "zh", longText, "vi", longText);
        });
        given(guideTranslationRepository.findByGuideIdAndLanguage(any(), anyString())).willReturn(Optional.empty());

        generator.regenerate(ko);

        ArgumentCaptor<GuideTranslation> captor = ArgumentCaptor.forClass(GuideTranslation.class);
        verify(guideTranslationRepository, times(3)).save(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(t -> {
            assertThat(t.getBadge()).hasSize(100).endsWith("…");
            assertThat(t.getTitle()).hasSize(255);
        });
    }
}
