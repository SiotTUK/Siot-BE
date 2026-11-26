package com.siot.backend.checklist.service;

import com.siot.backend.checklist.domain.GuideTranslation;
import com.siot.backend.checklist.repository.GuideTranslationRepository;
import com.siot.backend.translation.Translator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 항목(체크리스트·생활 변화 할 일·생활수칙) 한국어 원문으로 en/zh/vi 번역을 다시 만든다 (COM-02, RUL-13).
 * 원문이 바뀌면 호출한다. 제목 번역에 실패한 언어는 저장하지 않고 (화면은 한국어 원문 + '번역 준비 중'),
 * 제목 외 칸의 번역에 실패하면 그 칸만 비워 둔다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuideTranslationGenerator {

    private final GuideTranslationRepository guideTranslationRepository;
    private final Translator translator;

    public void regenerate(GuideTranslation ko) {
        Long guideId = ko.getGuideId();
        try {
            Map<String, String> titles = translateSafely(ko.getTitle());
            Map<String, String> summaries = translateSafely(ko.getSummary());
            Map<String, String> contents = translateSafely(ko.getContent());
            Map<String, String> badges = translateSafely(ko.getBadge());
            Map<String, String> places = translateSafely(ko.getPlace());
            Map<String, String> fees = translateSafely(ko.getFee());
            Map<String, String> laws = translateSafely(ko.getLaw());
            Map<String, String> penalties = translateSafely(ko.getPenalty());
            Map<String, String> sourceNames = translateSafely(ko.getSourceName());
            List<Map<String, String>> docs = ko.getDocs() == null ? List.of()
                    : ko.getDocs().stream().map(this::translateSafely).toList();

            for (String lang : Translator.TARGET_LANGUAGES) {
                if (!titles.containsKey(lang)) {
                    // 제목 번역이 없으면 그 언어는 건드리지 않는다. 원문을 번역인 것처럼 저장하면
                    // 화면이 '번역 준비 중'(COM-02) 대신 한국어를 번역본으로 보여 준다. 다음 원문 저장 때 다시 시도한다
                    log.warn("Skip {} translation of guide {}: title translation failed", lang, guideId);
                    continue;
                }
                GuideTranslation tr = guideTranslationRepository.findByGuideIdAndLanguage(guideId, lang)
                        .orElseGet(() -> GuideTranslation.create(guideId, lang));
                List<String> translatedDocs = new ArrayList<>();
                for (int i = 0; i < docs.size(); i++) {
                    translatedDocs.add(docs.get(i).getOrDefault(lang, ko.getDocs().get(i)));
                }
                tr.update(
                        titles.get(lang),
                        summaries.get(lang),
                        contents.get(lang),
                        badges.get(lang),
                        places.get(lang),
                        translatedDocs,
                        fees.get(lang),
                        laws.get(lang),
                        penalties.get(lang),
                        sourceNames.get(lang)
                );
                guideTranslationRepository.save(tr);
            }
        } catch (Exception e) {
            log.warn("Auto translation failed for guide {}: {}", guideId, e.getMessage());
        }
    }

    private Map<String, String> translateSafely(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return translator.translateAll(text);
        } catch (Exception e) {
            log.warn("Translation failed: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }
}
