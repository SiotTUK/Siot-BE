package com.siot.backend.global.common;

import com.siot.backend.TestcontainersConfiguration;
import com.siot.backend.auth.security.MemberPrincipal;
import com.siot.backend.member.domain.AppUser;
import com.siot.backend.member.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** BE #26: 지원하지 않는 language 쿼리는 모든 API 에서 400 INVALID_INPUT, 빈 값은 생략과 같다 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class QueryLanguageIntegrationTest {

    private static final List<String> PATHS = List.of(
            "/api/members/me/checklist",
            "/api/rules",
            "/api/rules/faq",
            "/api/rules/categories",
            "/api/schedules/monthly",
            "/api/schedules/this-month",
            "/api/schedules/yearly",
            "/api/schedules/taxes",
            "/api/chat/suggestions");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AppUserRepository users;

    @Test
    void unsupportedLanguageIsBadRequestEverywhere() throws Exception {
        AppUser account = users.save(AppUser.create("query-language@example.com", "unused"));
        for (String path : PATHS) {
            mockMvc.perform(get(path).param("language", "xx").with(user(MemberPrincipal.from(account))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
    }

    @Test
    void blankLanguageMeansOmitted() throws Exception {
        AppUser account = users.save(AppUser.create("query-language-blank@example.com", "unused"));
        for (String path : PATHS) {
            mockMvc.perform(get(path).param("language", "").with(user(MemberPrincipal.from(account))))
                    .andExpect(status().isOk());
        }
    }
}
