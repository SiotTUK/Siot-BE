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
            // #14 생활수칙 칸: 문장 목록·시기·언어별 문의처
            List<Map<String, String>> points = ko.getPoints() == null ? List.of()
                    : ko.getPoints().stream().map(this::translateSafely).toList();
            Map<String, String> timings = translateSafely(ko.getTiming());
            Map<String, String> contacts = translateSafely(ko.getContact());

            for (String lang : Translator.TARGET_LANGUAGES) {
                if (!titles.containsKey(lang)) {
                    // 제목 번역이 없으면 그 언어는 건드리지 않는다. 원문을 번역인 것처럼 저장하면
                    // 화면이 '번역 준비 중'(COM-02) 대신 한국어를 번역본으로 보여 준다. 다음 원문 저장 때 다시 시도한다
                    log.warn("Skip {} translation of guide {}: title translation failed", lang, guideId);
                    continue;
                }
                GuideTranslation tr = guideTranslationRepository.findByGuideIdAndLanguage(guideId, lang)
                        .orElseGet(() -> GuideTranslation.create(guideId, lang));
                if (tr.isReviewed()) {
                    // #14 사람이 확인한 번역은 자동 번역으로 덮어쓰지 않는다 (관리자 문구 저장으로만 고친다)
                    log.info("Keep reviewed {} translation of guide {}", lang, guideId);
                    continue;
                }
                List<String> translatedPoints = new ArrayList<>();
                for (int i = 0; i < points.size(); i++) {
                    translatedPoints.add(points.get(i).getOrDefault(lang, ko.getPoints().get(i)));
                }
                List<String> translatedDocs = new ArrayList<>();
                for (int i = 0; i < docs.size(); i++) {
                    translatedDocs.add(docs.get(i).getOrDefault(lang, ko.getDocs().get(i)));
                }
                // 번역문은 원문보다 길 수 있다 (예: 베트남어). DB 칸 길이를 넘으면 커밋 때 요청 전체가 실패하므로 자른다
                tr.update(
                        truncate(titles.get(lang), VARCHAR_DEFAULT),
                        summaries.get(lang),
                        contents.get(lang),
                        truncate(badges.get(lang), BADGE_LENGTH),
                        places.get(lang),
                        translatedDocs,
                        truncate(fees.get(lang), VARCHAR_DEFAULT),
                        truncate(laws.get(lang), VARCHAR_DEFAULT),
                        penalties.get(lang),
                        truncate(sourceNames.get(lang), VARCHAR_DEFAULT)
                );
                tr.updateRuleFields(translatedPoints, timings.get(lang), contacts.get(lang));
                guideTranslationRepository.save(tr);
            }
        } catch (Exception e) {
            log.warn("Auto translation failed for guide {}: {}", guideId, e.getMessage());
        }
    }

    /** guide_translation 의 VARCHAR 칸 길이 (title·fee·law·source_name 은 기본 255, badge 는 100) */
    static final int VARCHAR_DEFAULT = 255;
    static final int BADGE_LENGTH = 100;

    static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 1) + "…";
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
