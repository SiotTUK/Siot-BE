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
        given(guideRepository.save(any(Guide.class))).willAnswer(inv -> {
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
        verify(guideRepository).save(any(Guide.class));
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
