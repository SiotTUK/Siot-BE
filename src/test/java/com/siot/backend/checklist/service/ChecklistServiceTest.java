package com.siot.backend.checklist.service;

import com.siot.backend.checklist.domain.*;
import com.siot.backend.checklist.dto.ChecklistItemResponse;
import com.siot.backend.checklist.dto.ChecklistProgressUpdateRequest;
import com.siot.backend.checklist.dto.ChecklistSummaryResponse;
import com.siot.backend.checklist.repository.*;
import com.siot.backend.member.domain.AppUser;
import com.siot.backend.member.domain.Purpose;
import com.siot.backend.member.domain.Residence;
import com.siot.backend.member.domain.UserType;
import com.siot.backend.member.repository.AppUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChecklistServiceTest {

    @Mock
    private GuideRepository guideRepository;
    @Mock
    private GuideTranslationRepository guideTranslationRepository;
    @Mock
    private GuideVariantTranslationRepository variantTranslationRepository;
    @Mock
    private ChecklistProgressRepository progressRepository;
    @Mock
    private MemberLifeChangeRepository lifeChangeRepository;
    @Mock
    private AppUserRepository appUserRepository;

    @InjectMocks
    private ChecklistService checklistService;

    @Test
    @DisplayName("신규 입국자(NEW) 체크리스트 조회 및 진행률 계산 검증 (REQUIRED만 진행률에 반영)")
    void getMyChecklist_newcomer_success() {
        // given
        Long userId = 1L;
        AppUser user = AppUser.create("user@siot.kr", "hash");
        user.updateOnboarding(UserType.NEW, LocalDate.now().minusDays(10), Purpose.WORK, Set.of(Residence.CAR), LocalDate.now().plusMonths(6));

        given(appUserRepository.findById(userId)).willReturn(Optional.of(user));

        Guide cl01 = Guide.create("CL-01", "CHECKLIST", ItemType.REQUIRED, "기초 행정", 1, null, false);
        org.springframework.test.util.ReflectionTestUtils.setField(cl01, "id", 1L);
        Guide cl04 = Guide.create("CL-04", "CHECKLIST", ItemType.OPTIONAL, "금융", 4, null, false);
        org.springframework.test.util.ReflectionTestUtils.setField(cl04, "id", 2L);

        given(guideRepository.findByKindOrderBySortOrderAsc("CHECKLIST")).willReturn(List.of(cl01, cl04));
        given(guideRepository.findByKindOrderBySortOrderAsc("LIFE_CHANGE_TASK")).willReturn(Collections.emptyList());
        given(guideTranslationRepository.findByGuideIdInAndLanguage(anyCollection(), eq("ko"))).willReturn(List.of(
                GuideTranslation.create(cl01.getId(), "ko", "외국인등록", "안내 요약", "상세 내용", "90일 이내", "출입국", List.of("여권"), "30,000원", "출입국관리법", "과태료", "1345"),
                GuideTranslation.create(cl04.getId(), "ko", "체류 자격별 안내", "요약", "상세 내용", "안내", "기관", List.of("서류"), null, null, null, null)
        ));

        // CL-01은 DONE으로 완료됨
        ChecklistProgress progressCl01 = ChecklistProgress.of(userId, cl01.getId(), "", ProgressStatus.DONE);
        given(progressRepository.findByUserId(userId)).willReturn(List.of(progressCl01));
        given(lifeChangeRepository.findByUserId(userId)).willReturn(Collections.emptyList());

        // when
        ChecklistSummaryResponse summary = checklistService.getMyChecklist(userId, "ko");

        // then
        assertThat(summary.totalRequiredCount()).isEqualTo(1);
        assertThat(summary.completedRequiredCount()).isEqualTo(1);
        assertThat(summary.progressPercentage()).isEqualTo(100);
        assertThat(summary.items()).hasSize(2);

        // 미완료(TODO) 항목이 완료(DONE) 항목보다 우선 정렬됨
        ChecklistItemResponse item0 = summary.items().get(0);
        assertThat(item0.code()).isEqualTo("CL-04");
        assertThat(item0.status()).isEqualTo("TODO");
        assertThat(item0.completed()).isFalse();

        ChecklistItemResponse item1 = summary.items().get(1);
        assertThat(item1.code()).isEqualTo("CL-01");
        assertThat(item1.status()).isEqualTo("DONE");
        assertThat(item1.completed()).isTrue();
    }

    @Test
    @DisplayName("CL-05 체류기간 연장: 마감일은 만료일, 신청 시작일은 만료일 − 4개월, 만료 30일 안이면 강조 (CAL-04)")
    void getMyChecklist_stayExtension_applyStartAndHighlight() {
        // given: 만료 20일 전
        Long userId = 1L;
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
        LocalDate expiry = today.plusDays(20);
        AppUser user = AppUser.create("user@siot.kr", "hash");
        user.updateOnboarding(UserType.LIVING, today.minusYears(1), Purpose.WORK, Set.of(), expiry);
        given(appUserRepository.findById(userId)).willReturn(Optional.of(user));

        Guide cl05 = Guide.create("CL-05", "CHECKLIST", ItemType.REQUIRED, "IMMIGRATION", 5, null, false);
        org.springframework.test.util.ReflectionTestUtils.setField(cl05, "id", 5L);
        given(guideRepository.findByKindOrderBySortOrderAsc("CHECKLIST")).willReturn(List.of(cl05));
        given(guideRepository.findByKindOrderBySortOrderAsc("LIFE_CHANGE_TASK")).willReturn(Collections.emptyList());
        given(guideTranslationRepository.findByGuideIdInAndLanguage(anyCollection(), eq("ko"))).willReturn(List.of());
        given(progressRepository.findByUserId(userId)).willReturn(List.of());
        given(lifeChangeRepository.findByUserId(userId)).willReturn(Collections.emptyList());

        // when
        ChecklistItemResponse item = checklistService.getMyChecklist(userId, "ko").items().get(0);

        // then
        assertThat(item.code()).isEqualTo("CL-05");
        assertThat(item.deadline()).isEqualTo(expiry);
        assertThat(item.dDay()).isEqualTo(20L);
        assertThat(item.applyStartsOn()).isEqualTo(expiry.minusMonths(4));
        assertThat(item.highlighted()).isTrue();
    }

    @Test
    @DisplayName("CL-05 외 항목은 신청 시작일이 없고 강조하지 않는다")
    void getMyChecklist_otherItems_noApplyStart() {
        Long userId = 1L;
        AppUser user = AppUser.create("user@siot.kr", "hash");
        user.updateOnboarding(UserType.NEW, LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).minusDays(85),
                Purpose.WORK, Set.of(), null);
        given(appUserRepository.findById(userId)).willReturn(Optional.of(user));

        Guide cl01 = Guide.create("CL-01", "CHECKLIST", ItemType.REQUIRED, "IMMIGRATION", 1, null, false);
        org.springframework.test.util.ReflectionTestUtils.setField(cl01, "id", 1L);
        given(guideRepository.findByKindOrderBySortOrderAsc("CHECKLIST")).willReturn(List.of(cl01));
        given(guideRepository.findByKindOrderBySortOrderAsc("LIFE_CHANGE_TASK")).willReturn(Collections.emptyList());
        given(guideTranslationRepository.findByGuideIdInAndLanguage(anyCollection(), eq("ko"))).willReturn(List.of());
        given(progressRepository.findByUserId(userId)).willReturn(List.of());
        given(lifeChangeRepository.findByUserId(userId)).willReturn(Collections.emptyList());

        ChecklistItemResponse item = checklistService.getMyChecklist(userId, "ko").items().get(0);

        assertThat(item.code()).isEqualTo("CL-01");
        assertThat(item.applyStartsOn()).isNull();
        assertThat(item.highlighted()).isFalse();
    }

    @Test
    @DisplayName("체크리스트 상태 업데이트 - 완료(DONE) 저장")
    void updateProgress_done() {
        // given
        Long userId = 1L;
        String code = "CL-01";
        Guide guide = Guide.create(code, "CHECKLIST", ItemType.REQUIRED, "기초 행정", 1, null, false);
        org.springframework.test.util.ReflectionTestUtils.setField(guide, "id", 1L);

        given(guideRepository.findByCode(code)).willReturn(Optional.of(guide));
        given(progressRepository.findById(any(ChecklistProgressId.class))).willReturn(Optional.empty());

        ChecklistProgressUpdateRequest request = new ChecklistProgressUpdateRequest("", ProgressStatus.DONE);

        // when
        checklistService.updateProgress(userId, code, request);

        // then
        verify(progressRepository).save(any(ChecklistProgress.class));
    }
}
