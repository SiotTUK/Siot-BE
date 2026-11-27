package com.siot.backend.schedule.service;

import com.siot.backend.checklist.domain.Guide;
import com.siot.backend.checklist.domain.GuideTranslation;
import com.siot.backend.checklist.domain.StayExtension;
import com.siot.backend.checklist.repository.GuideRepository;
import com.siot.backend.checklist.repository.GuideTranslationRepository;
import com.siot.backend.global.common.SupportedLanguages;
import com.siot.backend.global.config.ClockConfig;
import com.siot.backend.global.exception.BusinessException;
import com.siot.backend.global.exception.ErrorCode;
import com.siot.backend.member.domain.AppUser;
import com.siot.backend.member.domain.Residence;
import com.siot.backend.member.repository.AppUserRepository;
import com.siot.backend.rag.domain.GuideDocumentReference;
import com.siot.backend.rag.repository.GuideDocumentReferenceRepository;
import com.siot.backend.schedule.domain.CalendarSchedule;
import com.siot.backend.schedule.domain.TaxKind;
import com.siot.backend.schedule.dto.MonthlyTodoResponse;
import com.siot.backend.schedule.dto.ScheduleResponse;
import com.siot.backend.schedule.dto.TaxDetailResponse;
import com.siot.backend.schedule.repository.CalendarScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 생활 달력 월간/연간 일정 및 개인 세금·체류 일정 병합 서비스 (CAL-01 ~ CAL-04)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarScheduleService {

    private final CalendarScheduleRepository calendarScheduleRepository;
    private final GuideTranslationRepository guideTranslationRepository;
    private final GuideRepository guideRepository;
    private final GuideDocumentReferenceRepository guideDocumentReferenceRepository;
    private final AppUserRepository appUserRepository;

    /** 조회할 수 있는 연도. 행사 달력(EventQueryService#calendar)과 같은 범위 */
    static final int MIN_YEAR = 2000;
    static final int MAX_YEAR = 2100;

    public List<ScheduleResponse> getMonthlySchedules(Long userId, int year, int month, String language) {
        checkYear(year);
        if (month < 1 || month > 12) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "month 는 1~12 입니다.");
        }
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.with(TemporalAdjusters.lastDayOfMonth());
        String targetLang = SupportedLanguages.orDefault(language);

        // 1. 공통 생활/세금 일정 조회
        List<CalendarSchedule> common = calendarScheduleRepository.findActiveOverlapping(start, end);
        List<ScheduleResponse> result = commonSchedules(common, targetLang);

        // 2. 로그인 회원의 개인 일정 (체류기간 만료 등) 병합 (CAL-04)
        if (userId != null) {
            TaxSchedules taxes = taxSchedules(common);
            appUserRepository.findById(userId).ifPresent(user ->
                    addPersonalSchedules(result, user, start, end, CalendarScheduleMessages.forLanguage(targetLang), taxes));
        }

        result.sort(Comparator.comparing(ScheduleResponse::startsOn));
        return result;
    }

    /**
     * CAL-02 메인의 이번 달 할일: 이번 달 일정 중 마감이 지나지 않은 것과 남은 일수.
     * 남은 것이 없으면 다음 달 일정을 보여 준다. 마감 임박 순.
     *
     * @param today 오늘 (한국 시간)
     */
    public MonthlyTodoResponse getMonthlyTodos(Long userId, String language, LocalDate today) {
        List<MonthlyTodoResponse.Item> items = todoItems(
                getMonthlySchedules(userId, today.getYear(), today.getMonthValue(), language), today);
        if (!items.isEmpty()) {
            return new MonthlyTodoResponse(today.getYear(), today.getMonthValue(), false, items);
        }
        LocalDate next = today.plusMonths(1);
        return new MonthlyTodoResponse(next.getYear(), next.getMonthValue(), true,
                todoItems(getMonthlySchedules(userId, next.getYear(), next.getMonthValue(), language), today));
    }

    private static List<MonthlyTodoResponse.Item> todoItems(List<ScheduleResponse> schedules, LocalDate today) {
        return schedules.stream()
                .map(s -> new MonthlyTodoResponse.Item(s,
                        java.time.temporal.ChronoUnit.DAYS.between(today, s.endsOn() != null ? s.endsOn() : s.startsOn())))
                .filter(item -> item.dDay() >= 0)
                .sorted(Comparator.comparingLong(MonthlyTodoResponse.Item::dDay))
                .toList();
    }

    /** 범위 밖 연도는 LocalDate 오류(500) 대신 400 */
    private static void checkYear(int year) {
        if (year < MIN_YEAR || year > MAX_YEAR) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "year 는 " + MIN_YEAR + "~" + MAX_YEAR + " 입니다.");
        }
    }

    /**
     * CAL-01 연간 일정: 해당 연도 1~12월의 공통 세금·생활 일정 (시작일 기준)과, 로그인 회원이면
     * 월간과 같은 규칙의 개인 일정(체류 만료·연장 신청 시작, 자차 → 자동차세, 자가 → 재산세)을 넣는다 (BE #20).
     *
     * @param userId 로그인 회원 ID. null 이면 공통 일정만
     */
    public List<ScheduleResponse> getYearlySchedules(Long userId, int year, String language) {
        checkYear(year);
        String targetLang = SupportedLanguages.orDefault(language);
        List<CalendarSchedule> common = calendarScheduleRepository.findByStartsOnBetweenAndActiveTrueOrderByStartsOnAsc(
                LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
        List<ScheduleResponse> result = commonSchedules(common, targetLang);
        if (userId != null) {
            TaxSchedules taxes = taxSchedules(common);
            appUserRepository.findById(userId).ifPresent(user -> {
                CalendarScheduleMessages messages = CalendarScheduleMessages.forLanguage(targetLang);
                // 월 단위 규칙(6·12월 자동차세, 7·9월 재산세)을 그대로 쓰도록 달마다 넣는다. 체류 일정은 그 날짜가 든 달에 한 번만 들어간다
                for (int month = 1; month <= 12; month++) {
                    LocalDate start = LocalDate.of(year, month, 1);
                    addPersonalSchedules(result, user, start, start.with(TemporalAdjusters.lastDayOfMonth()), messages, taxes);
                }
            });
        }
        result.sort(Comparator.comparing(ScheduleResponse::startsOn));
        return result;
    }

    private List<ScheduleResponse> commonSchedules(List<CalendarSchedule> schedules, String targetLang) {
        List<ScheduleResponse> result = new ArrayList<>();

        for (CalendarSchedule cs : schedules) {
            String title = cs.getKind();
            if (cs.getGuideId() != null) {
                // 번역이 없으면 한국어 제목 (COM-02). 내부 코드(TAX_CAR 등)를 제목으로 보여 주지 않는다
                GuideTranslation tr = guideTranslationRepository
                        .findByGuideIdAndLanguage(cs.getGuideId(), targetLang)
                        .or(() -> guideTranslationRepository.findByGuideIdAndLanguage(cs.getGuideId(), "ko"))
                        .orElse(null);
                if (tr != null) title = tr.getTitle();
            }

            result.add(new ScheduleResponse(
                    cs.getId(),
                    cs.getGuideId(),
                    cs.getKind(),
                    title,
                    cs.getStartsOn(),
                    cs.getEndsOn(),
                    cs.getTargetDescription(),
                    cs.getSourceUrl(),
                    false
            ).withTaxScheduleId(TaxKind.isTax(cs.getKind()) ? cs.getId() : null));
        }
        return result;
    }

    /**
     * 개인 세금 일정에서 열 공통 세금 상세를 찾는 표 (BE #29).
     *
     * @param guideCodes 공통 일정에 연결된 항목 ID → 코드 (tax-car 처럼 kind 가 아닌 항목으로 이어진 일정도 찾도록)
     */
    private record TaxSchedules(List<CalendarSchedule> schedules, Map<Long, String> guideCodes) {

        /**
         * 같은 종류이면서 [start, end] 에 걸친 공통 일정. 여럿이면 개인 일정 시작일이 든 것, 그다음 먼저 시작하는 것.
         *
         * @return 없으면 null
         */
        Long find(String kind, LocalDate start, LocalDate end, LocalDate personalStart) {
            return schedules.stream()
                    .filter(cs -> TaxKind.matches(cs, codeOf(guideCodes, cs), kind))
                    .filter(cs -> !cs.getStartsOn().isAfter(end) && !due(cs).isBefore(start))
                    // 개인 일정 시작일이 든 일정 먼저 (false < true)
                    .min(Comparator.comparing((CalendarSchedule cs) -> !covers(cs, personalStart))
                            .thenComparing(CalendarSchedule::getStartsOn)
                            .thenComparing(CalendarSchedule::getId))
                    .map(CalendarSchedule::getId)
                    .orElse(null);
        }

        private static boolean covers(CalendarSchedule cs, LocalDate date) {
            return !cs.getStartsOn().isAfter(date) && !due(cs).isBefore(date);
        }

        private static LocalDate due(CalendarSchedule cs) {
            return cs.getEndsOn() != null ? cs.getEndsOn() : cs.getStartsOn();
        }
    }

    private static String codeOf(Map<Long, String> guideCodes, CalendarSchedule cs) {
        return cs.getGuideId() == null ? null : guideCodes.get(cs.getGuideId());
    }

    private TaxSchedules taxSchedules(List<CalendarSchedule> common) {
        List<CalendarSchedule> taxes = common.stream()
                .filter(cs -> cs.getId() != null && TaxKind.isTax(cs.getKind()))
                .toList();
        return new TaxSchedules(taxes, guideCodes(taxes));
    }

    private Map<Long, String> guideCodes(List<CalendarSchedule> schedules) {
        Set<Long> guideIds = schedules.stream().map(CalendarSchedule::getGuideId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (guideIds.isEmpty()) {
            return Map.of();
        }
        return guideRepository.findAllById(guideIds).stream()
                .collect(Collectors.toMap(Guide::getId, Guide::getCode));
    }

    /** 로그인 회원의 개인 일정: 체류기간 연장·만료 (CAL-04), 자동차세·재산세 */
    private void addPersonalSchedules(List<ScheduleResponse> result, AppUser user, LocalDate start, LocalDate end,
                                      CalendarScheduleMessages messages, TaxSchedules taxes) {
        LocalDate expiry = user.getStayExpiry();
        if (expiry != null) {
            boolean highlighted = StayExtension.isHighlighted(expiry, LocalDate.now(ClockConfig.KST));
            LocalDate applyStart = StayExtension.applyStartDate(expiry);
            if (!applyStart.isBefore(start) && !applyStart.isAfter(end)) {
                result.add(new ScheduleResponse(
                        null,
                        null,
                        "STAY_EXTENSION_START",
                        messages.extensionTitle(),
                        applyStart,
                        applyStart,
                        messages.extensionWindow().formatted(applyStart, expiry),
                        "https://www.hikorea.go.kr",
                        true,
                        highlighted
                ));
            }
            if (!expiry.isBefore(start) && !expiry.isAfter(end)) {
                result.add(new ScheduleResponse(
                        null,
                        null,
                        "STAY_EXPIRY",
                        messages.expiryTitle(),
                        expiry,
                        expiry,
                        messages.expiryDescription().formatted(applyStart),
                        "https://www.hikorea.go.kr",
                        true,
                        highlighted
                ));
            }
        }

        int month = start.getMonthValue();
        // 개인 세금 일정은 scheduleId 가 없으므로, 세금 상세는 그 달의 같은 종류 공통 일정(taxScheduleId)으로 연다 (BE #29)
        if ((month == 6 || month == 12) && user.getResidences().contains(Residence.CAR)) {
            LocalDate taxStart = start.withDayOfMonth(16);
            result.add(new ScheduleResponse(
                    null, null, TaxKind.CAR, messages.carTaxTitle().formatted(month == 6 ? 1 : 2),
                    taxStart, end, messages.carTaxDescription(), "https://www.wetax.go.kr", true
            ).withTaxScheduleId(taxes.find(TaxKind.CAR, start, end, taxStart)));
        }

        if ((month == 7 || month == 9) && user.getResidences().contains(Residence.HOUSE)) {
            LocalDate taxStart = start.withDayOfMonth(16);
            result.add(new ScheduleResponse(
                    null, null, TaxKind.PROPERTY, messages.propertyTaxTitle().formatted(month == 7 ? 1 : 2),
                    taxStart, end, messages.propertyTaxDescription(), "https://www.wetax.go.kr", true
            ).withTaxScheduleId(taxes.find(TaxKind.PROPERTY, start, end, taxStart)));
        }
    }

    /**
     * CAL-03 세금 상세 일정 및 납부 안내 전용 조회
     */
    public TaxDetailResponse getTaxDetail(Long scheduleId, String language) {
        CalendarSchedule cs = calendarScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND));

        String targetLang = SupportedLanguages.orDefault(language);
        String title = cs.getKind();
        String summary = null;
        String content = null;
        String place = null;
        List<String> docs = Collections.emptyList();
        String fee = null;
        String law = null;
        String penalty = null;
        String contact = null;
        String sourcePages = null;

        if (cs.getGuideId() != null) {
            GuideTranslation tr = guideTranslationRepository.findByGuideIdAndLanguage(cs.getGuideId(), targetLang)
                    .or(() -> guideTranslationRepository.findByGuideIdAndLanguage(cs.getGuideId(), "ko"))
                    .orElse(null);

            if (tr != null) {
                title = tr.getTitle();
                summary = tr.getSummary();
                content = tr.getContent();
                place = tr.getPlace();
                docs = tr.getDocs() != null ? tr.getDocs() : Collections.emptyList();
                fee = tr.getFee();
                law = tr.getLaw();
                penalty = tr.getPenalty();
            }

            // 문의처: 생활수칙(RuleResponseAssembler)과 같은 저장 위치 — 언어별 문구, 없으면 항목의 문의처 (BE #29)
            String translatedContact = tr != null && tr.getContact() != null && !tr.getContact().isBlank()
                    ? tr.getContact() : null;
            contact = translatedContact != null ? translatedContact
                    : guideRepository.findById(cs.getGuideId()).map(Guide::getContact)
                    .filter(c -> !c.isBlank()).orElse(null);

            GuideDocumentReference docRef = guideDocumentReferenceRepository
                    .findFirstByGuideIdOrderByCreatedAtDesc(cs.getGuideId())
                    .orElse(null);
            if (docRef != null && docRef.getPageStart() != null) {
                sourcePages = (docRef.getPageEnd() != null && !docRef.getPageEnd().equals(docRef.getPageStart()))
                        ? String.format("p.%d~%d", docRef.getPageStart(), docRef.getPageEnd())
                        : String.format("p.%d", docRef.getPageStart());
            }
        }

        return new TaxDetailResponse(
                cs.getId(),
                cs.getGuideId(),
                cs.getKind(),
                title,
                cs.getStartsOn(),
                cs.getEndsOn(),
                cs.getTargetDescription(),
                cs.getSourceUrl(),
                summary,
                content,
                place,
                docs,
                fee,
                law,
                penalty,
                contact,
                sourcePages,
                ScheduleResponse.dDay(cs.getStartsOn(), cs.getEndsOn(), LocalDate.now(ClockConfig.KST))
        );
    }

    /**
     * CAL-03 전체 세금 상세 목록 조회
     */
    public List<TaxDetailResponse> getTaxSchedules(String language) {
        return getTaxSchedules(language, null);
    }

    /**
     * CAL-03 세금 상세 목록. kind 를 주면 그 세금만 (생활수칙 세금 항목의 taxKind, BE #29).
     * 공통 일정의 kind 가 같거나, 연결된 항목 코드가 그 종류(tax-car → TAX_CAR …)인 일정. 없으면 빈 목록
     */
    public List<TaxDetailResponse> getTaxSchedules(String language, String kind) {
        List<CalendarSchedule> schedules = calendarScheduleRepository.findByActiveTrueOrderByStartsOnAsc().stream()
                .filter(s -> TaxKind.isTax(s.getKind()))
                .toList();
        if (kind != null && !kind.isBlank()) {
            Map<Long, String> guideCodes = guideCodes(schedules);
            schedules = schedules.stream()
                    .filter(s -> TaxKind.matches(s, codeOf(guideCodes, s), kind.strip()))
                    .toList();
        }
        return schedules.stream()
                .map(s -> getTaxDetail(s.getId(), language))
                .toList();
    }
}
