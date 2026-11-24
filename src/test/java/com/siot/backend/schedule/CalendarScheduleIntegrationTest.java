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
}
