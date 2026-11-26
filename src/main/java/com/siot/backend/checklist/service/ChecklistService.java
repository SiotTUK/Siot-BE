package com.siot.backend.checklist.service;

import com.siot.backend.checklist.domain.*;
import com.siot.backend.checklist.dto.ChecklistItemResponse;
import com.siot.backend.checklist.dto.ChecklistProgressUpdateRequest;
import com.siot.backend.checklist.dto.ChecklistSummaryResponse;
import com.siot.backend.checklist.repository.ChecklistProgressRepository;
import com.siot.backend.checklist.repository.GuideRepository;
import com.siot.backend.checklist.repository.GuideTranslationRepository;
import com.siot.backend.checklist.repository.GuideVariantTranslationRepository;
import com.siot.backend.checklist.repository.MemberLifeChangeRepository;
import com.siot.backend.global.exception.BusinessException;
import com.siot.backend.global.exception.ErrorCode;
import com.siot.backend.member.domain.AppUser;
import com.siot.backend.member.domain.Purpose;
import com.siot.backend.member.domain.Residence;
import com.siot.backend.member.domain.UserType;
import com.siot.backend.member.repository.AppUserRepository;
import com.siot.backend.rag.domain.GuideDocumentReference;
import com.siot.backend.rag.repository.GuideDocumentReferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 체크리스트 규칙표(11/13) 기반 맞춤 체크리스트 계산 및 진행 상태 관리 서비스
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChecklistService {

    private final GuideRepository guideRepository;
    private final GuideTranslationRepository guideTranslationRepository;
    private final GuideVariantTranslationRepository guideVariantTranslationRepository;
    private final ChecklistProgressRepository checklistProgressRepository;
    private final MemberLifeChangeRepository memberLifeChangeRepository;
    private final AppUserRepository appUserRepository;
    private final GuideDocumentReferenceRepository guideDocumentReferenceRepository;

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    private static final String STAY_EXTENSION_CODE = "CL-05";

    /**
     * 회원의 온보딩 답과 생활 변화를 바탕으로 맞춤 체크리스트 및 진행률 계산
     */
    public ChecklistSummaryResponse getMyChecklist(Long userId, String language) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        String targetLang = (language != null && !language.isBlank()) ? language : user.getLanguage();
        if (targetLang == null) targetLang = "ko";

        LocalDate today = LocalDate.now(SEOUL_ZONE);

        // 1. 등록된 생활 변화 조회
        Map<String, LocalDate> lifeChanges = memberLifeChangeRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(MemberLifeChange::getLifeChangeCode, MemberLifeChange::getBaseDate));

        // 2. 사용자의 완료 이력 조회 (guideId_periodKey -> ChecklistProgress)
        Map<String, ChecklistProgress> progressMap = checklistProgressRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(
                        p -> p.getGuideId() + "_" + (p.getPeriodKey() == null ? "" : p.getPeriodKey()),
                        p -> p,
                        (p1, p2) -> p1
                ));

        // 3. 가이드 마스터 조회
        List<Guide> allGuides = new ArrayList<>();
        allGuides.addAll(guideRepository.findByKindOrderBySortOrderAsc("CHECKLIST"));
        allGuides.addAll(guideRepository.findByKindOrderBySortOrderAsc("LIFE_CHANGE_TASK"));

        // 번역본 일괄 조회
        Set<Long> guideIds = allGuides.stream().map(Guide::getId).collect(Collectors.toSet());
        Map<Long, GuideTranslation> translationMap = guideTranslationRepository.findByGuideIdInAndLanguage(guideIds, targetLang).stream()
                .collect(Collectors.toMap(GuideTranslation::getGuideId, t -> t, (t1, t2) -> t1));

        // 한국어 기본 번역본 (fallback)
        Map<Long, GuideTranslation> koTranslationMap = "ko".equals(targetLang) ? translationMap :
                guideTranslationRepository.findByGuideIdInAndLanguage(guideIds, "ko").stream()
                        .collect(Collectors.toMap(GuideTranslation::getGuideId, t -> t, (t1, t2) -> t1));

        // CHK-06 출처 안내책자 쪽수 조회
        Map<Long, GuideDocumentReference> docRefMap = guideDocumentReferenceRepository.findByGuideIdIn(guideIds).stream()
                .collect(Collectors.toMap(GuideDocumentReference::getGuideId, ref -> ref, (r1, r2) -> r1));

        List<ChecklistItemResponse> items = new ArrayList<>();

        for (Guide guide : allGuides) {
            if (guide.isDeleted()) continue;

            // 표시 조건 평가
            if (!shouldDisplayGuide(guide, user, lifeChanges)) {
                continue;
            }

            // 기한 및 회차 키 계산
            PeriodAndDeadline pd = calculatePeriodAndDeadline(guide, user, lifeChanges, today);
            String periodKey = pd.periodKey;
            LocalDate deadline = pd.deadline;

            // 진행 상태 평가 (행이 없으면 TODO 또는 OVERDUE)
            String progressKey = guide.getId() + "_" + periodKey;
            ChecklistProgress progress = progressMap.get(progressKey);

            String status;
            boolean isCompleted = false;

            if (progress != null) {
                status = progress.getStatus().name();
                isCompleted = (progress.getStatus() == ProgressStatus.DONE);
            } else {
                if (deadline != null && deadline.isBefore(today)) {
                    status = "OVERDUE";
                } else {
                    status = "TODO";
                }
            }

            // D-day 계산
            Long dDay = null;
            String dDayText = null;
            if (deadline != null) {
                dDay = ChronoUnit.DAYS.between(today, deadline);
                if (status.equals("OVERDUE") || dDay < 0) {
                    dDayText = "기한 지남";
                } else if (dDay == 0) {
                    dDayText = "D-Day";
                } else {
                    dDayText = "D-" + dDay;
                }
            }

            // CHK-08 경고 표시 (외국인등록 D-30, 체류지 변경 D-7, 기한 초과 등)
            boolean warning = false;
            String warningMessage = null;
            String code = guide.getCode();

            if ("OVERDUE".equals(status)) {
                warning = true;
                warningMessage = "마감 기한이 초과되었습니다. 즉시 확인 후 조치하세요.";
            } else if ("CL-01".equals(code) || "CL-02".equals(code)) {
                if (dDay != null && dDay <= 30) {
                    warning = true;
                    warningMessage = "외국인등록 기한이 30일 이내로 임박했습니다. 기한 초과 시 과태료 및 불이익이 발생합니다.";
                }
            } else if ("LC-01-1".equals(code)) {
                if (dDay != null && dDay <= 7) {
                    warning = true;
                    warningMessage = "체류지 변경 신고 마감(전입 후 15일 이내) 기한이 7일 이내로 임박했습니다. 기한 초과 시 과태료가 부과됩니다.";
                }
            }

            // CHK-06 쪽수
            GuideDocumentReference docRef = docRefMap.get(guide.getId());
            Integer pageStart = docRef != null ? docRef.getPageStart() : null;
            Integer pageEnd = docRef != null ? docRef.getPageEnd() : null;
            String sourcePages = null;
            if (pageStart != null) {
                sourcePages = (pageEnd != null && !pageEnd.equals(pageStart))
                        ? String.format("p.%d~%d", pageStart, pageEnd)
                        : String.format("p.%d", pageStart);
            }

            // 번역본 매핑
            GuideTranslation tr = translationMap.getOrDefault(guide.getId(), koTranslationMap.get(guide.getId()));

            // 조건별 문구 (CL-04 등)
            List<String> variantBodies = getVariantBodies(guide, targetLang, user.effectivePurpose());

            // CL-05 체류기간 연장: 마감일 = 체류 만료일 (CAL-04)
            boolean isStayExtension = STAY_EXTENSION_CODE.equals(guide.getCode());

            boolean hasNoticeChanged = guide.getNoticeChangedAt() != null &&
                    guide.getNoticeChangedAt().isAfter(OffsetDateTime.now().minusDays(14));

            items.add(new ChecklistItemResponse(
                    guide.getCode(),
                    tr != null ? tr.getTitle() : guide.getCode(),
                    guide.getKind(),
                    guide.getItemType(),
                    tr != null ? tr.getBadge() : null,
                    tr != null ? tr.getPlace() : null,
                    tr != null && tr.getDocs() != null ? tr.getDocs() : Collections.emptyList(),
                    tr != null ? tr.getFee() : null,
                    tr != null ? tr.getLaw() : null,
                    tr != null ? tr.getPenalty() : null,
                    guide.getContact(),
                    guide.getSourceUrl(),
                    tr != null ? tr.getSourceName() : null,
                    tr != null ? tr.getSummary() : null,
                    tr != null ? tr.getContent() : null,
                    variantBodies,
                    deadline,
                    dDay,
                    dDayText,
                    status,
                    isCompleted,
                    periodKey,
                    guide.getNoticeChangedAt(),
                    hasNoticeChanged,
                    isStayExtension ? StayExtension.applyStartDate(deadline) : null,
                    isStayExtension && StayExtension.isHighlighted(deadline, today),
                    warning,
                    warningMessage,
                    pageStart,
                    pageEnd,
                    sourcePages
            ));
        }

        // 정렬: 미완료 우선 -> D-day 임박순 -> 정렬순서
        items.sort(Comparator.comparing(ChecklistItemResponse::completed)
                .thenComparing((ChecklistItemResponse item) -> item.deadline() == null)
                .thenComparing(ChecklistItemResponse::deadline, Comparator.nullsLast(Comparator.naturalOrder())));

        // 필수(REQUIRED) 기준 진행률 계산
        List<ChecklistItemResponse> requiredItems = items.stream()
                .filter(i -> i.itemType() == ItemType.REQUIRED)
                .toList();

        int totalRequired = requiredItems.size();
        int completedRequired = (int) requiredItems.stream().filter(ChecklistItemResponse::completed).count();
        int percentage = (totalRequired > 0) ? (completedRequired * 100) / totalRequired : 0;

        return new ChecklistSummaryResponse(totalRequired, completedRequired, percentage, items);
    }

    /**
     * 체크리스트 항목 상태(DONE / NOT_APPLICABLE) 변경 (CHK-07)
     */
    @Transactional
    public void updateProgress(Long userId, String code, ChecklistProgressUpdateRequest request) {
        Guide guide = guideRepository.findByCode(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.GUIDE_NOT_FOUND));

        String periodKey = request.periodKey() != null ? request.periodKey() : ChecklistProgress.ONCE;
        ChecklistProgressId id = new ChecklistProgressId(userId, guide.getId(), periodKey);

        if (request.status() == null) {
            // 선택 해제 시 기록 삭제 -> TODO 로 복귀
            checklistProgressRepository.findById(id).ifPresent(checklistProgressRepository::delete);
        } else {
            ChecklistProgress progress = checklistProgressRepository.findById(id)
                    .orElseGet(() -> ChecklistProgress.of(userId, guide.getId(), periodKey, request.status()));
            progress.changeStatus(request.status());
            checklistProgressRepository.save(progress);
        }
    }

    // --- 규칙표 표시 조건 평가 (docs/erd.md 반영) ---

    private boolean shouldDisplayGuide(Guide guide, AppUser user, Map<String, LocalDate> lifeChanges) {
        String code = guide.getCode();
        UserType userType = user.getUserType();
        Set<Residence> res = user.getResidences();
        Purpose purpose = user.effectivePurpose();

        // 1. 체크리스트 (CL-xx)
        if ("CL-01".equals(code)) return userType == UserType.NEW && !res.contains(Residence.F4);
        if ("CL-02".equals(code)) return userType == UserType.NEW && res.contains(Residence.F4);
        if ("CL-04".equals(code)) return true; // 항상
        if ("CL-05".equals(code)) return user.getStayExpiry() != null;
        if ("CL-06".equals(code)) return purpose == Purpose.STUDY;
        if ("CL-07".equals(code)) return purpose == Purpose.WORK;
        if ("CL-08".equals(code)) return purpose == Purpose.FAMILY;
        if ("CL-09".equals(code)) return purpose == Purpose.OTHER;
        if ("CL-10".equals(code)) return res.contains(Residence.CAR);
        if ("CL-11".equals(code)) return res.contains(Residence.HOUSE);
        if ("CL-12".equals(code)) return res.contains(Residence.KIDS);
        if ("CL-13".equals(code)) return userType == UserType.LIVING && res.contains(Residence.F4);
        if ("CL-20".equals(code) || "CL-21".equals(code) || "CL-22".equals(code)) {
            return userType == UserType.NEW;
        }

        // 2. 생활 변화 할 일 (LC-xx-n)
        String lcCode = guide.getLifeChangeCode();
        if (lcCode == null || !lifeChanges.containsKey(lcCode)) {
            return false; // 등록되지 않은 생활 변화는 표시 안 함
        }

        if (ResidenceChangeReport.isReportCode(code)) {
            return code.equals(ResidenceChangeReport.reportCodeFor(res.contains(Residence.F4)));
        }
        if ("LC-01-3".equals(code)) return res.contains(Residence.CAR);
        if ("LC-01-4".equals(code)) return res.contains(Residence.KIDS);
        if ("LC-01-5".equals(code)) return true;

        if ("LC-02-1".equals(code)) return purpose == Purpose.WORK;
        if ("LC-02-2".equals(code)) return purpose == Purpose.WORK;
        if ("LC-02-3".equals(code)) return purpose == Purpose.STUDY;
        if ("LC-02-4".equals(code)) return true;

        return true; // 그 외 LC 할 일은 해당 변화 등록 시 기본 표시
    }

    // --- 기한 및 회차 키 계산 (docs/erd.md 반영) ---

    private record PeriodAndDeadline(String periodKey, LocalDate deadline) {}

    private PeriodAndDeadline calculatePeriodAndDeadline(Guide guide, AppUser user, Map<String, LocalDate> lifeChanges, LocalDate today) {
        String code = guide.getCode();
        int year = today.getYear();

        // 1. 단발성 체크리스트
        if ("CL-01".equals(code) || "CL-02".equals(code)) {
            LocalDate deadline = user.getEntryDate() != null ? user.getEntryDate().plusDays(90) : null;
            return new PeriodAndDeadline(ChecklistProgress.ONCE, deadline);
        }

        if (STAY_EXTENSION_CODE.equals(code)) {
            return new PeriodAndDeadline(ChecklistProgress.ONCE, user.getStayExpiry());
        }

        // 2. 세금 정기 회차 (CL-10 자동차세, CL-11 재산세)
        if ("CL-10".equals(code)) {
            // 자동차세: 6월(6/16~6/30), 12월(12/16~12/31)
            if (today.isBefore(LocalDate.of(year, 7, 1))) {
                return new PeriodAndDeadline(year + "-06", LocalDate.of(year, 6, 30));
            } else {
                return new PeriodAndDeadline(year + "-12", LocalDate.of(year, 12, 31));
            }
        }

        if ("CL-11".equals(code)) {
            // 재산세: 7월(7/16~7/31), 9월(9/16~9/30)
            if (today.isBefore(LocalDate.of(year, 8, 1))) {
                return new PeriodAndDeadline(year + "-07", LocalDate.of(year, 7, 31));
            } else {
                return new PeriodAndDeadline(year + "-09", LocalDate.of(year, 9, 30));
            }
        }

        // 3. 생활 변화 할 일 (period_key = base_date)
        String lcCode = guide.getLifeChangeCode();
        if (lcCode != null && lifeChanges.containsKey(lcCode)) {
            LocalDate baseDate = lifeChanges.get(lcCode);
            String periodKey = baseDate.toString();

            LocalDate deadline = null;
            if (ResidenceChangeReport.isReportCode(code)) deadline = ResidenceChangeReport.deadline(code, baseDate);
            else if ("LC-01-3".equals(code)) deadline = baseDate.plusDays(30);
            else if ("LC-02-2".equals(code)) deadline = baseDate.plusDays(15);
            else if ("LC-04-1".equals(code)) deadline = baseDate.plusDays(15);
            else if ("LC-05-1".equals(code) || "LC-06-1".equals(code)) deadline = baseDate.plusDays(90);
            else if ("LC-08-1".equals(code)) deadline = baseDate.plusDays(15);

            return new PeriodAndDeadline(periodKey, deadline);
        }

        return new PeriodAndDeadline(ChecklistProgress.ONCE, null);
    }

    private List<String> getVariantBodies(Guide guide, String language, Purpose purpose) {
        if (!"CL-04".equals(guide.getCode())) {
            return Collections.emptyList();
        }

        List<GuideVariantTranslation> variants = guideVariantTranslationRepository.findByGuideIdAndLanguage(guide.getId(), language);
        if (variants.isEmpty()) {
            variants = guideVariantTranslationRepository.findByGuideIdAndLanguage(guide.getId(), "ko");
        }

        String targetKey = purpose != null ? purpose.name() : Purpose.OTHER.name();
        List<String> bodies = new ArrayList<>();

        for (GuideVariantTranslation v : variants) {
            if (targetKey.equals(v.getVariantKey()) || "WARNING".equals(v.getVariantKey())) {
                bodies.add(v.getBody());
            }
        }
        return bodies;
    }
}
