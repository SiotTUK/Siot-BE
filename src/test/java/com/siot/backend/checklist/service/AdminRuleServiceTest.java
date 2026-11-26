package com.siot.backend.checklist.service;

import com.siot.backend.checklist.domain.Guide;
import com.siot.backend.checklist.domain.GuideTranslation;
import com.siot.backend.checklist.domain.ItemType;
import com.siot.backend.checklist.dto.RuleCreateRequest;
import com.siot.backend.checklist.dto.RuleResponse;
import com.siot.backend.checklist.dto.RuleUpdateRequest;
import com.siot.backend.checklist.repository.GuideRepository;
import com.siot.backend.checklist.repository.GuideTranslationRepository;
import com.siot.backend.notification.service.NotificationService;
import com.siot.backend.rag.repository.GuideDocumentReferenceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminRuleServiceTest {

    @Mock
    private GuideRepository guideRepository;
    @Mock
    private GuideTranslationRepository guideTranslationRepository;
    @Mock
    private GuideDocumentReferenceRepository guideDocumentReferenceRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private GuideTranslationGenerator guideTranslationGenerator;
    @Mock
    private RuleCategoryService ruleCategoryService;
    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-11-20T12:00:00Z"), ZoneId.of("Asia/Seoul"));

    @InjectMocks
    private AdminRuleService adminRuleService;

    @Test
    @DisplayName("관리자 생활수칙 등록(RUL-09) 및 다국어 자동 번역 생성(RUL-13) 검증")
    void createRule_with_auto_translation() {
        // given
        Long adminId = 99L;
        RuleCreateRequest req = new RuleCreateRequest(
                "RUL-09", "쓰레기 배출", 10, false, "031-310-2114", "https://siheung.go.kr",
                "대형 폐기물 스티커 배출 방법", "요약", "본문", "스티커 부착", "지정 장소",
                List.of("스티커"), "수수료", "법령", "과태료", "시흥시청"
        );

        given(guideRepository.findByCode("RUL-09")).willReturn(Optional.empty());
        given(guideRepository.saveAndFlush(any(Guide.class))).willAnswer(inv -> {
            Guide g = inv.getArgument(0);
            ReflectionTestUtils.setField(g, "id", 100L);
            return g;
        });

        // when
        RuleResponse res = adminRuleService.createRule(adminId, req);

        // then
        assertThat(res.code()).isEqualTo("RUL-09");
        assertThat(res.title()).isEqualTo("대형 폐기물 스티커 배출 방법");
        assertThat(res.categoryName()).isEqualTo("쓰레기 배출");
        verify(guideRepository).saveAndFlush(any(Guide.class));
        verify(guideTranslationRepository, org.mockito.Mockito.atLeastOnce()).save(any(GuideTranslation.class));
        // RUL-13: 한국어 원문으로 en/zh/vi 번역, 카테고리명 번역
        verify(guideTranslationGenerator).regenerate(org.mockito.ArgumentMatchers.argThat(
                t -> "ko".equals(t.getLanguage()) && "대형 폐기물 스티커 배출 방법".equals(t.getTitle())));
        verify(ruleCategoryService).ensureTranslations("쓰레기 배출");
    }

    @Test
    @DisplayName("관리자 생활수칙 수정(RUL-10) 및 안내 변경 알림 트리거(NTF-07) 검증")
    void updateRule_triggers_notice_changed_notification() {
        // given
        Long adminId = 99L;
        String code = "RUL-01";
        Guide guide = Guide.create(code, "RULE", ItemType.INFO, "쓰레기 배출", 1, null, true);
        ReflectionTestUtils.setField(guide, "id", 50L);

        given(guideRepository.findByCode(code)).willReturn(Optional.of(guide));
        given(guideTranslationRepository.findByGuideIdAndLanguage(50L, "ko"))
                .willReturn(Optional.of(GuideTranslation.create(50L, "ko")));

        RuleUpdateRequest req = new RuleUpdateRequest(
                "쓰레기 배출", 1, true, "031-111", "https://siheung.go.kr",
                "종량제 봉투 배출 규정 개정", "요약", "본문", "배지", "장소",
                Collections.emptyList(), "수수료 인상", "법령", "과태료", "출처",
                true // noticeChanged = true
        );

        // when
        RuleResponse res = adminRuleService.updateRule(adminId, code, req);

        // then
        assertThat(res.code()).isEqualTo("RUL-01");
        verify(notificationService).createNoticeChangedNotifications(guide);
        // COM-02: 원문이 바뀌면 번역을 다시 만든다
        verify(guideTranslationGenerator).regenerate(any(GuideTranslation.class));
    }

    @Test
    @DisplayName("삭제한 생활수칙이 쓰던 코드로 등록하면 409 DUPLICATE_GUIDE_CODE (DB unique 위반 500 이 아니라)")
    void createRule_withDeletedCode_conflict() {
        Guide deleted = Guide.create("RUL-03", "RULE", ItemType.INFO, "교통질서", 1, null, false);
        deleted.softDelete(1L, java.time.OffsetDateTime.now());
        given(guideRepository.findByCode("RUL-03")).willReturn(Optional.of(deleted));
        RuleCreateRequest req = new RuleCreateRequest(
                " RUL-03 ", "교통질서", 1, false, null, null, "제목", null, "본문", null, null,
                null, null, null, null, null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> adminRuleService.createRule(99L, req))
                .isInstanceOf(com.siot.backend.global.exception.BusinessException.class)
                .extracting(e -> ((com.siot.backend.global.exception.BusinessException) e).getErrorCode())
                .isEqualTo(com.siot.backend.global.exception.ErrorCode.DUPLICATE_GUIDE_CODE);
        org.mockito.Mockito.verify(guideRepository, org.mockito.Mockito.never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("관리자 목록: 삭제한 항목은 기본으로 빼고, 번역이 없는 언어를 알려 준다")
    void getRules_excludesDeletedAndReportsMissingTranslations() {
        Guide active = Guide.create("RUL-01", "RULE", ItemType.INFO, "쓰레기 배출", 2, null, true);
        ReflectionTestUtils.setField(active, "id", 1L);
        Guide deleted = Guide.create("RUL-02", "RULE", ItemType.INFO, "쓰레기 배출", 1, null, false);
        ReflectionTestUtils.setField(deleted, "id", 2L);
        deleted.softDelete(1L, java.time.OffsetDateTime.now());
        given(guideRepository.findByKindOrderBySortOrderAsc("RULE")).willReturn(List.of(deleted, active));
        given(guideTranslationRepository.findByGuideIdIn(List.of(1L))).willReturn(List.of(
                GuideTranslation.create(1L, "ko", "음식물 쓰레기", null, "본문", null, null, null, null, null, null, null),
                GuideTranslation.create(1L, "en", "Food waste", null, null, null, null, null, null, null, null, null)));

        List<com.siot.backend.checklist.dto.AdminRuleSummaryResponse> result = adminRuleService.getRules(null, null, false);

        assertThat(result).singleElement().satisfies(r -> {
            assertThat(r.code()).isEqualTo("RUL-01");
            assertThat(r.title()).isEqualTo("음식물 쓰레기");
            assertThat(r.faq()).isTrue();
            assertThat(r.translatedLanguages()).containsExactly("en");
            assertThat(r.missingLanguages()).containsExactly("zh", "vi");
        });
    }

    @Test
    @DisplayName("관리자 목록: includeDeleted=true 면 삭제한 항목도 넣고, 검색어는 코드·한국어 제목에서 찾는다")
    void getRules_includeDeletedAndKeyword() {
        Guide a = Guide.create("RUL-01", "RULE", ItemType.INFO, "교통질서", 1, null, false);
        ReflectionTestUtils.setField(a, "id", 1L);
        Guide b = Guide.create("RUL-02", "RULE", ItemType.INFO, "교통질서", 2, null, false);
        ReflectionTestUtils.setField(b, "id", 2L);
        b.softDelete(1L, java.time.OffsetDateTime.now());
        given(guideRepository.findByKindOrderBySortOrderAsc("RULE")).willReturn(List.of(a, b));
        given(guideTranslationRepository.findByGuideIdIn(List.of(1L, 2L))).willReturn(List.of(
                GuideTranslation.create(1L, "ko", "무단횡단 금지", null, "본문", null, null, null, null, null, null, null),
                GuideTranslation.create(2L, "ko", "자전거 대여", null, "본문", null, null, null, null, null, null, null)));

        assertThat(adminRuleService.getRules("교통질서", null, true))
                .extracting(com.siot.backend.checklist.dto.AdminRuleSummaryResponse::code).containsExactly("RUL-01", "RUL-02");
        assertThat(adminRuleService.getRules(null, "자전거", true))
                .singleElement().satisfies(r -> assertThat(r.deleted()).isTrue());
    }

    @Test
    @DisplayName("관리자 생활수칙 소프트 삭제(RUL-11, ADM-06) 검증")
    void deleteRule_soft_deletes() {
        // given
        Long adminId = 99L;
        String code = "RUL-01";
        Guide guide = Guide.create(code, "RULE", ItemType.INFO, "쓰레기 배출", 1, null, true);
        ReflectionTestUtils.setField(guide, "id", 50L);

        given(guideRepository.findByCode(code)).willReturn(Optional.of(guide));

        // when
        adminRuleService.deleteRule(adminId, code);

        // then
        assertThat(guide.isDeleted()).isTrue();
    }
}
