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
