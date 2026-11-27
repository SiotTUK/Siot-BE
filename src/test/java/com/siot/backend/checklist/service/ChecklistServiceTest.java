package com.siot.backend.checklist.service;

import com.siot.backend.checklist.domain.*;
import com.siot.backend.checklist.dto.ChecklistItemResponse;
import com.siot.backend.checklist.dto.ChecklistProgressUpdateRequest;
import com.siot.backend.checklist.dto.ChecklistSummaryResponse;
import com.siot.backend.checklist.repository.*;
import com.siot.backend.rag.repository.GuideDocumentReferenceRepository;
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
    @Mock
    private GuideDocumentReferenceRepository guideDocumentReferenceRepository;

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
        given(guideDocumentReferenceRepository.findByGuideIdIn(anyCollection())).willReturn(Collections.emptyList());

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

    @Test
    @DisplayName("CHK-08 외국인등록 마감 D-30 이내 임박 시 경고 플래그 및 경고 문구 노출 검증")
    void getMyChecklist_warning_on_d30() {
        // given
        Long userId = 1L;
        AppUser user = AppUser.create("user@siot.kr", "hash");
        // 입국 후 70일 경과 -> 90일 만료까지 D-20 (D-30 이내)
        user.updateOnboarding(UserType.NEW, LocalDate.now().minusDays(70), Purpose.WORK, Set.of(Residence.CAR), LocalDate.now().plusMonths(6));

        given(appUserRepository.findById(userId)).willReturn(Optional.of(user));

        Guide cl01 = Guide.create("CL-01", "CHECKLIST", ItemType.REQUIRED, "기초 행정", 1, null, false);
        org.springframework.test.util.ReflectionTestUtils.setField(cl01, "id", 1L);

        given(guideRepository.findByKindOrderBySortOrderAsc("CHECKLIST")).willReturn(List.of(cl01));
        given(guideRepository.findByKindOrderBySortOrderAsc("LIFE_CHANGE_TASK")).willReturn(Collections.emptyList());
        given(guideTranslationRepository.findByGuideIdInAndLanguage(anyCollection(), eq("ko"))).willReturn(List.of(
                GuideTranslation.create(cl01.getId(), "ko", "외국인등록", "요약", "본문", "90일", "출입국", List.of(), null, null, null, null)
        ));
        given(progressRepository.findByUserId(userId)).willReturn(Collections.emptyList());
        given(lifeChangeRepository.findByUserId(userId)).willReturn(Collections.emptyList());
        given(guideDocumentReferenceRepository.findByGuideIdIn(anyCollection())).willReturn(Collections.emptyList());

        // when
        ChecklistSummaryResponse summary = checklistService.getMyChecklist(userId, "ko");

        // then
        assertThat(summary.items()).hasSize(1);
        ChecklistItemResponse item = summary.items().get(0);
        assertThat(item.code()).isEqualTo("CL-01");
        assertThat(item.dDay()).isLessThanOrEqualTo(30L);
        assertThat(item.warning()).isTrue();
        assertThat(item.warningMessage()).contains("외국인등록");
        assertThat(item.warningType()).isEqualTo("REGISTRATION_DUE");
    }

    @Test
    @DisplayName("CHK-08 경고 문구는 요청 언어로, 종류 코드는 언어와 상관없이 같다 (BE #18)")
    void warningMessage_followsRequestedLanguage() {
        Long userId = 1L;
        AppUser user = AppUser.create("warn-lang@siot.kr", "hash");
        user.updateOnboarding(UserType.NEW, LocalDate.now().minusDays(70), Purpose.WORK, Set.of(), null);
        given(appUserRepository.findById(userId)).willReturn(Optional.of(user));
        Guide cl01 = Guide.create("CL-01", "CHECKLIST", ItemType.REQUIRED, "기초 행정", 1, null, false);
        org.springframework.test.util.ReflectionTestUtils.setField(cl01, "id", 1L);
        given(guideRepository.findByKindOrderBySortOrderAsc("CHECKLIST")).willReturn(List.of(cl01));
        given(guideRepository.findByKindOrderBySortOrderAsc("LIFE_CHANGE_TASK")).willReturn(Collections.emptyList());
        given(guideTranslationRepository.findByGuideIdInAndLanguage(anyCollection(), anyString())).willReturn(List.of());
        given(progressRepository.findByUserId(userId)).willReturn(Collections.emptyList());
        given(lifeChangeRepository.findByUserId(userId)).willReturn(Collections.emptyList());
        given(guideDocumentReferenceRepository.findByGuideIdIn(anyCollection())).willReturn(Collections.emptyList());

        for (String language : List.of("ko", "en", "zh", "vi")) {
            ChecklistItemResponse item = checklistService.getMyChecklist(userId, language).items().get(0);
            assertThat(item.warningType()).isEqualTo("REGISTRATION_DUE");
            assertThat(item.warningMessage()).isEqualTo(ChecklistWarning.REGISTRATION_DUE.message(language));
        }
        assertThat(ChecklistWarning.REGISTRATION_DUE.message("en")).startsWith("Your foreigner registration");
    }

    @Test
    @DisplayName("완료 체크는 체크리스트 항목만: 생활수칙·지운 항목은 404 (CHK-07)")
    void updateProgress_rejectsRuleAndDeletedGuides() {
        Guide rule = Guide.create("RUL-01", "RULE", ItemType.INFO, "쓰레기 배출", 1, null, false);
        given(guideRepository.findByCode("RUL-01")).willReturn(Optional.of(rule));
        Guide deleted = Guide.create("CL-99", "CHECKLIST", ItemType.REQUIRED, "기초 행정", 1, null, false);
        deleted.softDelete(1L, java.time.OffsetDateTime.now());
        given(guideRepository.findByCode("CL-99")).willReturn(Optional.of(deleted));
        var request = new com.siot.backend.checklist.dto.ChecklistProgressUpdateRequest(null, ProgressStatus.DONE);

        for (String code : List.of("RUL-01", "CL-99")) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> checklistService.updateProgress(1L, code, request))
                    .isInstanceOf(com.siot.backend.global.exception.BusinessException.class)
                    .extracting(e -> ((com.siot.backend.global.exception.BusinessException) e).getErrorCode())
                    .isEqualTo(com.siot.backend.global.exception.ErrorCode.GUIDE_NOT_FOUND);
        }
        org.mockito.Mockito.verify(progressRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("진행률: 해당 없음 필수 항목은 분모에서 뺀다. 모두 해당 없음이면 0/0·0% (BE #27)")
    void progress_excludesNotApplicableRequiredItems() {
        Long userId = 1L;
        AppUser user = AppUser.create("progress@siot.kr", "hash");
        user.updateOnboarding(UserType.NEW, LocalDate.now().minusDays(10), Purpose.WORK, Set.of(), null);
        given(appUserRepository.findById(userId)).willReturn(Optional.of(user));

        Guide cl01 = Guide.create("CL-01", "CHECKLIST", ItemType.REQUIRED, "기초 행정", 1, null, false);
        org.springframework.test.util.ReflectionTestUtils.setField(cl01, "id", 1L);
        Guide cl04 = Guide.create("CL-04", "CHECKLIST", ItemType.REQUIRED, "건강보험", 2, null, false);
        org.springframework.test.util.ReflectionTestUtils.setField(cl04, "id", 2L);
        Guide cl07 = Guide.create("CL-07", "CHECKLIST", ItemType.REQUIRED, "취업", 3, null, false);
        org.springframework.test.util.ReflectionTestUtils.setField(cl07, "id", 3L);
        given(guideRepository.findByKindOrderBySortOrderAsc("CHECKLIST")).willReturn(List.of(cl01, cl04, cl07));
        given(guideRepository.findByKindOrderBySortOrderAsc("LIFE_CHANGE_TASK")).willReturn(Collections.emptyList());
        given(guideTranslationRepository.findByGuideIdInAndLanguage(anyCollection(), eq("ko"))).willReturn(List.of());
        given(lifeChangeRepository.findByUserId(userId)).willReturn(Collections.emptyList());
        given(guideDocumentReferenceRepository.findByGuideIdIn(anyCollection())).willReturn(Collections.emptyList());

        // CL-01 완료, CL-07 해당 없음, CL-04 할 일
        given(progressRepository.findByUserId(userId)).willReturn(List.of(
                ChecklistProgress.of(userId, 1L, "", ProgressStatus.DONE),
                ChecklistProgress.of(userId, 3L, "", ProgressStatus.NOT_APPLICABLE)));
        ChecklistSummaryResponse summary = checklistService.getMyChecklist(userId, "ko");
        assertThat(summary.items()).extracting(ChecklistItemResponse::code).contains("CL-01", "CL-04", "CL-07");
        assertThat(summary.totalRequiredCount()).isEqualTo(2);
        assertThat(summary.completedRequiredCount()).isEqualTo(1);
        assertThat(summary.progressPercentage()).isEqualTo(50);

        // 모두 해당 없음
        given(progressRepository.findByUserId(userId)).willReturn(List.of(
                ChecklistProgress.of(userId, 1L, "", ProgressStatus.NOT_APPLICABLE),
                ChecklistProgress.of(userId, 2L, "", ProgressStatus.NOT_APPLICABLE),
                ChecklistProgress.of(userId, 3L, "", ProgressStatus.NOT_APPLICABLE)));
        ChecklistSummaryResponse allNa = checklistService.getMyChecklist(userId, "ko");
        assertThat(allNa.totalRequiredCount()).isZero();
        assertThat(allNa.completedRequiredCount()).isZero();
        assertThat(allNa.progressPercentage()).isZero();
    }

    @Test
    @DisplayName("COM-02 본문 언어: 요청 언어 번역 / 한국어 대체 / 문구 없음 / 조건부 문구만 대체를 구분한다 (BE #30)")
    void getMyChecklist_contentLanguageAndTranslated() {
        Long userId = 1L;
        AppUser user = AppUser.create("user@siot.kr", "hash");
        user.updateOnboarding(UserType.NEW, LocalDate.now().minusDays(10), Purpose.WORK, Set.of(),
                LocalDate.now().plusMonths(6));
        given(appUserRepository.findById(userId)).willReturn(Optional.of(user));

        Guide cl01 = guide("CL-01", 1L);   // 영어 번역 있음
        Guide cl07 = guide("CL-07", 7L);   // 한국어 원문만
        Guide cl05 = guide("CL-05", 5L);   // 문구 없음
        Guide cl04 = guide("CL-04", 4L);   // 본문은 영어, 조건부 문구는 한국어만
        given(guideRepository.findByKindOrderBySortOrderAsc("CHECKLIST")).willReturn(List.of(cl01, cl07, cl05, cl04));
        given(guideRepository.findByKindOrderBySortOrderAsc("LIFE_CHANGE_TASK")).willReturn(Collections.emptyList());
        given(guideTranslationRepository.findByGuideIdInAndLanguage(anyCollection(), eq("en"))).willReturn(List.of(
                translation(1L, "en", "Foreigner registration"), translation(4L, "en", "Health insurance")));
        given(guideTranslationRepository.findByGuideIdInAndLanguage(anyCollection(), eq("ko"))).willReturn(List.of(
                translation(1L, "ko", "외국인등록"), translation(7L, "ko", "근로계약"), translation(4L, "ko", "건강보험")));
        given(variantTranslationRepository.findByGuideIdAndLanguage(4L, "en")).willReturn(List.of());
        given(variantTranslationRepository.findByGuideIdAndLanguage(4L, "ko")).willReturn(List.of(
                variant(4L, "ko", "WORK", "직장 가입자는 회사에서 가입합니다.")));
        given(progressRepository.findByUserId(userId)).willReturn(List.of());
        given(lifeChangeRepository.findByUserId(userId)).willReturn(Collections.emptyList());

        java.util.Map<String, ChecklistItemResponse> en = checklistService.getMyChecklist(userId, "en").items().stream()
                .collect(java.util.stream.Collectors.toMap(ChecklistItemResponse::code, i -> i));

        assertThat(en.get("CL-01").contentLanguage()).isEqualTo("en");
        assertThat(en.get("CL-01").translated()).isTrue();
        assertThat(en.get("CL-01").title()).isEqualTo("Foreigner registration");

        assertThat(en.get("CL-07").contentLanguage()).isEqualTo("ko");
        assertThat(en.get("CL-07").translated()).isFalse();
        assertThat(en.get("CL-07").title()).isEqualTo("근로계약");

        assertThat(en.get("CL-05").contentLanguage()).isNull();
        assertThat(en.get("CL-05").translated()).isFalse();
        assertThat(en.get("CL-05").title()).isEqualTo("CL-05");
        // 경고·기한 표시는 본문 언어와 상관없이 그대로
        assertThat(en.get("CL-05").deadline()).isEqualTo(user.getStayExpiry());

        assertThat(en.get("CL-04").contentLanguage()).isEqualTo("en");
        assertThat(en.get("CL-04").variantBodies()).containsExactly("직장 가입자는 회사에서 가입합니다.");
        assertThat(en.get("CL-04").translated()).isFalse();

        // 한국어 요청: 원문이 있으면 translated, 없으면 false
        java.util.Map<String, ChecklistItemResponse> ko = checklistService.getMyChecklist(userId, "ko").items().stream()
                .collect(java.util.stream.Collectors.toMap(ChecklistItemResponse::code, i -> i));
        assertThat(ko.get("CL-07").contentLanguage()).isEqualTo("ko");
        assertThat(ko.get("CL-07").translated()).isTrue();
        assertThat(ko.get("CL-04").translated()).isTrue();
        assertThat(ko.get("CL-05").contentLanguage()).isNull();
        assertThat(ko.get("CL-05").translated()).isFalse();
    }

    private static Guide guide(String code, Long id) {
        Guide guide = Guide.create(code, "CHECKLIST", ItemType.REQUIRED, "IMMIGRATION", id.intValue(), null, false);
        org.springframework.test.util.ReflectionTestUtils.setField(guide, "id", id);
        return guide;
    }

    private static GuideTranslation translation(Long guideId, String language, String title) {
        return GuideTranslation.create(guideId, language, title, null, null, null, null, null, null, null, null, null);
    }

    private static GuideVariantTranslation variant(Long guideId, String language, String key, String body) {
        GuideVariantTranslation variant = org.springframework.beans.BeanUtils.instantiateClass(GuideVariantTranslation.class);
        org.springframework.test.util.ReflectionTestUtils.setField(variant, "guideId", guideId);
        org.springframework.test.util.ReflectionTestUtils.setField(variant, "language", language);
        org.springframework.test.util.ReflectionTestUtils.setField(variant, "variantKey", key);
        org.springframework.test.util.ReflectionTestUtils.setField(variant, "body", body);
        return variant;
    }

    @Test
    @DisplayName("세션은 있는데 회원이 없으면 다른 본인 API처럼 401 UNAUTHORIZED (BE #24)")
    void getMyChecklist_missingMember_isUnauthorized() {
        given(appUserRepository.findById(99L)).willReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> checklistService.getMyChecklist(99L, "ko"))
                .isInstanceOf(com.siot.backend.global.exception.BusinessException.class)
                .extracting(e -> ((com.siot.backend.global.exception.BusinessException) e).getErrorCode())
                .isEqualTo(com.siot.backend.global.exception.ErrorCode.UNAUTHORIZED);
    }
}
