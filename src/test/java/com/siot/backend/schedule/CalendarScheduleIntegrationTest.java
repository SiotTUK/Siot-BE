package com.siot.backend.schedule;

import com.siot.backend.TestcontainersConfiguration;
import com.siot.backend.auth.security.MemberPrincipal;
import com.siot.backend.member.domain.AppUser;
import com.siot.backend.member.repository.AppUserRepository;
import com.siot.backend.schedule.domain.CalendarSchedule;
import com.siot.backend.schedule.repository.CalendarScheduleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class CalendarScheduleIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    CalendarScheduleRepository schedules;

    @Autowired
    AppUserRepository users;

    @Autowired
    com.siot.backend.checklist.repository.GuideRepository guides;

    @Test
    void monthlyApiIncludesActiveSchedulesThatOverlapEitherMonth() throws Exception {
        AppUser account = users.save(AppUser.create("calendar-overlap@example.com", "unused"));
        schedules.saveAllAndFlush(List.of(
                CalendarSchedule.create(null, "TEST_SPANNING", LocalDate.of(2099, 11, 28), LocalDate.of(2099, 12, 1),
                        null, null, true, null),
                CalendarSchedule.create(null, "TEST_SINGLE_DAY", LocalDate.of(2099, 12, 31), null,
                        null, null, true, null),
                CalendarSchedule.create(null, "TEST_INACTIVE", LocalDate.of(2099, 12, 1), null,
                        null, null, false, null),
                CalendarSchedule.create(null, "TEST_NEXT_MONTH", LocalDate.of(2100, 1, 1), null,
                        null, null, true, null)
        ));

        mockMvc.perform(get("/api/schedules/monthly").param("year", "2099").param("month", "11")
                        .with(user(MemberPrincipal.from(account))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].kind", containsInAnyOrder("TEST_SPANNING")));

        mockMvc.perform(get("/api/schedules/monthly").param("year", "2099").param("month", "12")
                        .with(user(MemberPrincipal.from(account))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].kind", containsInAnyOrder("TEST_SPANNING", "TEST_SINGLE_DAY")))
                .andExpect(jsonPath("$.data[0].startsOn").value("2099-11-28"))
                .andExpect(jsonPath("$.data[0].endsOn").value("2099-12-01"));
    }

    /**
     * BE #29: 개인 자동차세·재산세는 scheduleId 가 없으므로 taxScheduleId 로 그 회차의 공통 세금 상세를 연다.
     * 세금 상세의 문의처는 연결된 항목의 언어별 문의처. 생활수칙 세금 항목은 taxKind 로 상세 목록을 찾는다.
     */
    @Test
    void personalTaxSchedulesAndTaxRulesLinkToCommonTaxDetail() throws Exception {
        AppUser account = AppUser.create("calendar-tax-link@example.com", "unused");
        account.updateOnboarding(com.siot.backend.member.domain.UserType.LIVING, LocalDate.of(2090, 1, 1),
                com.siot.backend.member.domain.Purpose.WORK,
                java.util.Set.of(com.siot.backend.member.domain.Residence.CAR, com.siot.backend.member.domain.Residence.HOUSE),
                null);
        account = users.save(account);
        Long carGuideId = guides.findByCode("tax-car").orElseThrow().getId();
        // 1기분은 kind 로, 2기분은 연결된 항목(tax-car)으로 같은 세금임을 안다. 재산세는 7월분만, 항목 연결 없음
        CalendarSchedule june = CalendarSchedule.create(carGuideId, "TAX_CAR", LocalDate.of(2099, 6, 16),
                LocalDate.of(2099, 6, 30), null, null, true, null);
        CalendarSchedule december = CalendarSchedule.create(carGuideId, "TAX_LOCAL", LocalDate.of(2099, 12, 16),
                LocalDate.of(2099, 12, 31), null, null, true, null);
        CalendarSchedule july = CalendarSchedule.create(null, "TAX_PROPERTY", LocalDate.of(2099, 7, 16),
                LocalDate.of(2099, 7, 31), null, null, true, null);
        schedules.saveAllAndFlush(List.of(june, december, july));
        MemberPrincipal principal = MemberPrincipal.from(account);
        String personalCar = "$.data[?(@.kind == 'TAX_CAR' && @.personal == true)].taxScheduleId";
        String personalProperty = "$.data[?(@.kind == 'TAX_PROPERTY' && @.personal == true)].taxScheduleId";

        // 월간: 개인 일정은 그 달 회차의 공통 일정, 공통 세금 일정은 자기 ID
        mockMvc.perform(get("/api/schedules/monthly").param("year", "2099").param("month", "6").with(user(principal)))
                .andExpect(jsonPath(personalCar, contains(june.getId().intValue())))
                .andExpect(jsonPath("$.data[?(@.personal == false)].taxScheduleId", contains(june.getId().intValue())));
        mockMvc.perform(get("/api/schedules/monthly").param("year", "2099").param("month", "12").with(user(principal)))
                .andExpect(jsonPath(personalCar, contains(december.getId().intValue())));
        // 대응하는 공통 일정이 없는 회차(9월 재산세)는 null
        mockMvc.perform(get("/api/schedules/monthly").param("year", "2099").param("month", "9").with(user(principal)))
                .andExpect(jsonPath(personalProperty, contains(org.hamcrest.Matchers.nullValue())));

        // 연간: 회차마다 그 달의 공통 일정
        mockMvc.perform(get("/api/schedules/yearly").param("year", "2099").with(user(principal)))
                .andExpect(jsonPath(personalCar, contains(june.getId().intValue(), december.getId().intValue())))
                .andExpect(jsonPath(personalProperty, contains(july.getId().intValue(), null)));

        // 세금 상세 문의처: 연결된 항목의 요청 언어 문의처, 항목이 없으면 null
        mockMvc.perform(get("/api/schedules/taxes/{id}", june.getId()).with(user(principal)))
                .andExpect(jsonPath("$.data.contact").value("시흥시 시세관리과 031-310-2093"));
        mockMvc.perform(get("/api/schedules/taxes/{id}", june.getId()).param("language", "en").with(user(principal)))
                .andExpect(jsonPath("$.data.contact").value("Siheung City Tax Management Division 031-310-2093"));
        mockMvc.perform(get("/api/schedules/taxes/{id}", july.getId()).with(user(principal)))
                .andExpect(jsonPath("$.data.contact").value(org.hamcrest.Matchers.nullValue()));

        // 생활수칙 세금 항목: taxKind 로 회차별 공통 상세를 찾는다
        mockMvc.perform(get("/api/rules/{code}", "tax-car").with(user(principal)))
                .andExpect(jsonPath("$.data.taxKind").value("TAX_CAR"));
        mockMvc.perform(get("/api/schedules/taxes").param("kind", "TAX_CAR").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].scheduleId",
                        org.hamcrest.Matchers.hasItems(june.getId().intValue(), december.getId().intValue())))
                .andExpect(jsonPath("$.data[*].scheduleId",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(july.getId().intValue()))));
    }

    /** BE #25: 범위 밖 연·월은 LocalDate 오류(500)가 아니라 400 INVALID_INPUT (행사 달력과 같은 범위) */
    @Test
    void outOfRangeYearOrMonthIsBadRequest() throws Exception {
        AppUser account = users.save(AppUser.create("calendar-range@example.com", "unused"));

        for (String[] params : List.of(
                new String[]{"/api/schedules/monthly", "2026", "13"},
                new String[]{"/api/schedules/monthly", "2026", "0"},
                new String[]{"/api/schedules/monthly", "1999", "1"},
                new String[]{"/api/schedules/monthly", "2101", "1"})) {
            mockMvc.perform(get(params[0]).param("year", params[1]).param("month", params[2])
                            .with(user(MemberPrincipal.from(account))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
        for (String year : List.of("1999", "2101", "1000000000")) {
            mockMvc.perform(get("/api/schedules/yearly").param("year", year)
                            .with(user(MemberPrincipal.from(account))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
        mockMvc.perform(get("/api/schedules/monthly").param("year", "2100").param("month", "12")
                        .with(user(MemberPrincipal.from(account))))
                .andExpect(status().isOk());
    }
}
