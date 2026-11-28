package com.siot.backend.checklist.service;

import com.siot.backend.checklist.domain.*;
import com.siot.backend.checklist.dto.AdminRuleSummaryResponse;
import com.siot.backend.checklist.dto.RuleCreateRequest;
import com.siot.backend.checklist.dto.RuleResponse;
import com.siot.backend.checklist.dto.RuleUpdateRequest;
import com.siot.backend.checklist.repository.GuideRepository;
import com.siot.backend.checklist.repository.GuideTranslationRepository;
import com.siot.backend.global.exception.BusinessException;
import com.siot.backend.global.exception.ErrorCode;
import com.siot.backend.notification.service.NotificationService;
import com.siot.backend.rag.domain.GuideDocumentReference;
import com.siot.backend.rag.repository.GuideDocumentReferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminRuleService {

    private final GuideRepository guideRepository;
    private final GuideTranslationRepository guideTranslationRepository;
    private final GuideDocumentReferenceRepository guideDocumentReferenceRepository;
    private final NotificationService notificationService;
    private final GuideTranslationGenerator guideTranslationGenerator;
    private final RuleCategoryService ruleCategoryService;
    private final RuleResponseAssembler assembler;
    private final com.siot.backend.checklist.repository.GuideSourceRepository guideSourceRepository;
    private final Clock clock;

    /**
     * RUL-09, ADM-06: 관리자 생활수칙 등록
     * RUL-13: 등록 시 다국어(en, zh, vi) 자동 번역 생성
     */
    @Transactional
    public RuleResponse createRule(Long adminId, RuleCreateRequest request) {
        String code = request.code().strip();
        String category = request.category().strip();
        // guide.code 는 삭제한 행까지 unique 다. 삭제한 생활수칙의 코드도 다시 쓸 수 없다
        // (이미 보낸 알림·오류 신고가 그 행을 가리키므로 되살려 덮어쓰지 않는다, RUL-11)
        if (guideRepository.findByCode(code).isPresent()) {
            throw new BusinessException(ErrorCode.DUPLICATE_GUIDE_CODE);
        }

        String content = contentOf(request.content(), request.points());
        OffsetDateTime now = OffsetDateTime.now(clock);
        Guide guide = Guide.create(code, "RULE", ItemType.INFO, category, request.sortOrder(), null, request.faq());
        guide.updateRule(category, request.sortOrder(), request.faq(), blankToNull(request.contact()),
                firstSourceUrl(request.sources(), blankToNull(request.sourceUrl())), adminId, now);
        guide.updateCheckedOn(request.checkedOn());
        try {
            guide = guideRepository.saveAndFlush(guide);
        } catch (DataIntegrityViolationException e) {
            // 위 확인과 저장 사이에 같은 코드가 들어온 경우
            throw new BusinessException(ErrorCode.DUPLICATE_GUIDE_CODE);
        }

        // 한국어 기본 번역본 저장
        GuideTranslation ko = GuideTranslation.create(guide.getId(), "ko");
        ko.update(
                request.title().strip(),
                blankToNull(request.summary()),
                content,
                blankToNull(request.badge()),
                blankToNull(request.place()),
                request.docs() == null ? List.of() : request.docs().stream().map(String::strip).toList(),
                blankToNull(request.fee()),
                blankToNull(request.law()),
                blankToNull(request.penalty()),
                blankToNull(request.sourceName())
        );
        ko.updateRuleFields(points(request.points()), blankToNull(request.when()), blankToNull(request.contact()));
        guideTranslationRepository.save(ko);
        replaceSources(guide.getId(), request.sources());

        // RUL-13: 자동 번역 실행 (en, zh, vi) - 본문과 카테고리명
        guideTranslationGenerator.regenerate(ko);
        ruleCategoryService.ensureTranslations(guide.getCategory());

        return toRuleResponse(guide, ko);
    }

    /**
     * RUL-10, ADM-06: 관리자 생활수칙 수정
     * NTF-07: noticeChanged=true 시 변경 알림 발송 유도
     * RUL-13: 수정 시 다국어 자동 번역 갱신
     */
    @Transactional
    public RuleResponse updateRule(Long adminId, String code, RuleUpdateRequest request) {
        Guide guide = guideRepository.findByCode(code)
                .filter(g -> "RULE".equals(g.getKind()) && !g.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.GUIDE_NOT_FOUND));

        String content = contentOf(request.content(), request.points());
        OffsetDateTime now = OffsetDateTime.now(clock);
        String category = request.category() != null ? request.category().strip() : null; // null = 그대로
        guide.updateRule(category, request.sortOrder() != null ? request.sortOrder() : guide.getSortOrder(),
                request.faq() != null ? request.faq() : guide.isFaq(),
                request.contact(), firstSourceUrl(request.sources(), request.sourceUrl()), adminId, now);
        guide.updateCheckedOn(request.checkedOn());
        guide.recordEdit(adminId, request.noticeChanged(), now);

        GuideTranslation ko = guideTranslationRepository.findByGuideIdAndLanguage(guide.getId(), "ko")
                .orElseGet(() -> GuideTranslation.create(guide.getId(), "ko"));
        ko.update(
                request.title().strip(),
                blankToNull(request.summary()),
                content,
                blankToNull(request.badge()),
                blankToNull(request.place()),
                request.docs() == null ? List.of() : request.docs().stream().map(String::strip).toList(),
                blankToNull(request.fee()),
                blankToNull(request.law()),
                blankToNull(request.penalty()),
                blankToNull(request.sourceName())
        );
        ko.updateRuleFields(points(request.points()), blankToNull(request.when()),
                request.contact() != null ? blankToNull(request.contact()) : ko.getContact());
        guideTranslationRepository.save(ko);
        if (request.sources() != null) {
            replaceSources(guide.getId(), request.sources());
        }

        // RUL-13, COM-02: 원문이 바뀌었으므로 번역을 다시 만든다 (사람이 확인한 번역은 그대로). 새 카테고리면 카테고리명도 번역한다
        guideTranslationGenerator.regenerate(ko);
        ruleCategoryService.ensureTranslations(guide.getCategory());

        // NTF-07: 실제 중요 안내 내용 변경 시 변경 알림 발송
        if (Boolean.TRUE.equals(request.noticeChanged())) {
            notificationService.createNoticeChangedNotifications(guide);
        }

        return toRuleResponse(guide, ko);
    }

    /**
     * ADM-06: 관리자 생활수칙 목록. 카테고리 → 정렬 순서 → 코드 순.
     *
     * @param category       카테고리 (한국어). 비우면 전체
     * @param keyword        코드 또는 한국어 제목에 들어 있는 글자 (대소문자 무시). 비우면 전체
     * @param includeDeleted true 면 삭제한 생활수칙도 넣는다
     */
    @Transactional(readOnly = true)
    public List<AdminRuleSummaryResponse> getRules(String category, String keyword, boolean includeDeleted) {
        String categoryFilter = blankToNull(category);
        String needle = keyword == null || keyword.isBlank() ? null : keyword.strip().toLowerCase(Locale.ROOT);

        List<Guide> rules = guideRepository.findByKindOrderBySortOrderAsc("RULE").stream()
                .filter(g -> includeDeleted || !g.isDeleted())
                .filter(g -> categoryFilter == null || categoryFilter.equals(g.getCategory()))
                .toList();
        if (rules.isEmpty()) {
            return List.of();
        }
        Map<Long, List<GuideTranslation>> translations = guideTranslationRepository
                .findByGuideIdIn(rules.stream().map(Guide::getId).toList()).stream()
                .collect(java.util.stream.Collectors.groupingBy(GuideTranslation::getGuideId));

        return rules.stream()
                .map(g -> toSummary(g, translations.getOrDefault(g.getId(), List.of())))
                .filter(r -> needle == null || r.code().toLowerCase(Locale.ROOT).contains(needle)
                        || (r.title() != null && r.title().toLowerCase(Locale.ROOT).contains(needle)))
                .sorted(Comparator.comparing(AdminRuleSummaryResponse::category, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparingInt(AdminRuleSummaryResponse::sortOrder)
                        .thenComparing(AdminRuleSummaryResponse::code))
                .toList();
    }

    private static AdminRuleSummaryResponse toSummary(Guide guide, List<GuideTranslation> translations) {
        String title = translations.stream().filter(t -> "ko".equals(t.getLanguage()))
                .map(GuideTranslation::getTitle).findFirst().orElse(guide.getCode());
        Set<String> languages = new HashSet<>();
        translations.forEach(t -> languages.add(t.getLanguage()));
        List<String> translated = com.siot.backend.translation.Translator.TARGET_LANGUAGES.stream()
                .filter(languages::contains).toList();
        List<String> missing = com.siot.backend.translation.Translator.TARGET_LANGUAGES.stream()
                .filter(lang -> !languages.contains(lang)).toList();
        return new AdminRuleSummaryResponse(guide.getCode(), guide.getCategory(), title, guide.getSortOrder(),
                guide.isFaq(), guide.isDeleted(), guide.getDeletedAt(), guide.getUpdatedAt(),
                guide.getNoticeChangedAt(), translated, missing);
    }

    /** ADM-06: 수정 폼을 채울 한국어 원문 전체. 삭제한 생활수칙도 읽는다 */
    @Transactional(readOnly = true)
    public RuleResponse getRule(String code) {
        Guide guide = guideRepository.findByCode(code)
                .filter(g -> "RULE".equals(g.getKind()))
                .orElseThrow(() -> new BusinessException(ErrorCode.GUIDE_NOT_FOUND));
        GuideTranslation ko = guideTranslationRepository.findByGuideIdAndLanguage(guide.getId(), "ko")
                .orElseThrow(() -> new BusinessException(ErrorCode.GUIDE_NOT_FOUND));
        return toRuleResponse(guide, ko);
    }

    /**
     * RUL-11, ADM-06: 관리자 생활수칙 소프트 삭제
     */
    @Transactional
    public void deleteRule(Long adminId, String code) {
        Guide guide = guideRepository.findByCode(code)
                .filter(g -> "RULE".equals(g.getKind()) && !g.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.GUIDE_NOT_FOUND));

        guide.softDelete(adminId, OffsetDateTime.now(clock));
    }

    private RuleResponse toRuleResponse(Guide guide, GuideTranslation tr) {
        return assembler.assembleKorean(guide, tr);
    }

    /** 본문: 보낸 본문, 없으면 문장 목록을 줄바꿈으로 이은 글. 둘 다 없으면 400 */
    private static String contentOf(String content, List<String> points) {
        String value = blankToNull(content);
        if (value != null) {
            return value;
        }
        List<String> lines = points(points);
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "본문(content) 또는 문장 목록(points)을 보내 주세요.");
        }
        return String.join("\n", lines);
    }

    private static List<String> points(List<String> points) {
        return points == null ? List.of() : points.stream().map(String::strip).filter(p -> !p.isEmpty()).toList();
    }

    /** 출처 목록을 보내면 첫 출처 링크, 아니면 sourceUrl */
    private static String firstSourceUrl(List<com.siot.backend.checklist.dto.RuleSourceRequest> sources, String sourceUrl) {
        return sources != null && !sources.isEmpty() ? sources.get(0).url().strip() : sourceUrl;
    }

    private void replaceSources(Long guideId, List<com.siot.backend.checklist.dto.RuleSourceRequest> sources) {
        guideSourceRepository.deleteByGuideId(guideId);
        if (sources == null) {
            return;
        }
        for (int i = 0; i < sources.size(); i++) {
            guideSourceRepository.save(GuideSource.of(guideId, i + 1, sources.get(i).name().strip(),
                    sources.get(i).url().strip()));
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
