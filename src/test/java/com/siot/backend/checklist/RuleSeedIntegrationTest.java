package com.siot.backend.checklist;

import com.siot.backend.TestcontainersConfiguration;
import com.siot.backend.auth.service.SignupVerificationService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #14 시드 마이그레이션 뒤 생활수칙 API 응답을 FE content.ts 와 항목별로 대조한다.
 * 기준 데이터 src/test/resources/rules/fe-rules.json 은 scripts/generate_rule_seed.mjs 가 FE 원본에서 만든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RuleSeedIntegrationTest {

    private static final List<String> LANGUAGES = List.of("ko", "en", "zh", "vi");
    private static final Map<String, String> FACT_FIELDS = Map.of(
            "where", "place", "when", "when", "fee", "fee", "law", "law", "penalty", "penalty", "contact", "contact");

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    StringRedisTemplate redisTemplate;
    @Autowired
    JsonMapper jsonMapper;

    private Cookie member;
    private JsonNode fe;

    @BeforeEach
    void setUp() throws Exception {
        member = signup("rule-seed-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com");
        fe = jsonMapper.readTree(new ClassPathResource("rules/fe-rules.json").getInputStream());
    }

    @Test
    void everyRuleMatchesFeContentInAllLanguages() throws Exception {
        for (String lang : LANGUAGES) {
            JsonNode api = data(get("/api/rules").param("language", lang));
            List<String> apiCodes = new ArrayList<>();
            api.forEach(rule -> apiCodes.add(rule.get("code").asString()));
            List<String> feCodes = new ArrayList<>();
            fe.get("rules").forEach(rule -> feCodes.add(rule.get("code").asString()));
            // FE 순서 그대로 (카테고리 순서 → 카테고리 안 순서). 이전 뒤 BE 에서 더한 항목(FE #73 등)은 사이에 들어갈 수 있다
            assertThat(apiCodes).as("codes (%s)", lang).containsAll(feCodes);
            assertThat(apiCodes.stream().filter(feCodes::contains).toList()).as("order (%s)", lang).isEqualTo(feCodes);

            for (JsonNode expected : fe.get("rules")) {
                String code = expected.get("code").asString();
                JsonNode actual = find(api, code);
                String where = code + " (" + lang + ")";
                assertThat(actual.get("categoryKey").asString()).as(where).isEqualTo(expected.get("category").asString());
                assertThat(actual.get("title").asString()).as(where).isEqualTo(expected.get("title").get(lang).asString());
                assertThat(actual.get("summary").asString()).as(where).isEqualTo(expected.get("summary").get(lang).asString());
                assertThat(actual.get("translated").asBoolean()).as(where).isTrue();
                assertThat(actual.get("checkedOn").asString()).as(where).isEqualTo(fe.get("checkedOn").asString());

                List<String> points = new ArrayList<>();
                actual.get("points").forEach(p -> points.add(p.asString()));
                List<String> expectedPoints = new ArrayList<>();
                expected.get("points").forEach(p -> expectedPoints.add(p.get(lang).asString()));
                assertThat(points).as(where + " points").isEqualTo(expectedPoints);

                JsonNode facts = expected.get("facts");
                for (Map.Entry<String, String> fact : FACT_FIELDS.entrySet()) {
                    JsonNode value = facts == null ? null : facts.get(fact.getKey());
                    JsonNode got = actual.get(fact.getValue());
                    if (value == null) {
                        assertThat(got == null || got.isNull()).as(where + " " + fact.getKey()).isTrue();
                    } else {
                        assertThat(got.asString()).as(where + " " + fact.getKey()).isEqualTo(value.get(lang).asString());
                    }
                }

                assertThat(actual.get("sources").size()).as(where + " sources").isEqualTo(expected.get("sources").size());
                for (int i = 0; i < expected.get("sources").size(); i++) {
                    assertThat(actual.get("sources").get(i).get("name").asString())
                            .isEqualTo(expected.get("sources").get(i).get("name").asString());
                    assertThat(actual.get("sources").get(i).get("url").asString())
                            .isEqualTo(expected.get("sources").get(i).get("url").asString());
                }
            }
        }
    }

    @Test
    void categoriesAndFaqsMatchFeContent() throws Exception {
        for (String lang : LANGUAGES) {
            JsonNode categories = data(get("/api/rules/categories").param("language", lang));
            JsonNode expected = fe.get("categories");
            for (int i = 0; i < expected.size(); i++) {
                assertThat(categories.get(i).get("key").asString()).isEqualTo(expected.get(i).get("key").asString());
                assertThat(categories.get(i).get("name").asString()).isEqualTo(expected.get(i).get("names").get(lang).asString());
            }

            JsonNode faqs = data(get("/api/rules/faq").param("language", lang));
            JsonNode expectedFaqs = fe.get("faqs");
            assertThat(faqs.size()).isEqualTo(expectedFaqs.size());
            for (int i = 0; i < expectedFaqs.size(); i++) {
                assertThat(faqs.get(i).get("question").asString()).isEqualTo(expectedFaqs.get(i).get("question").get(lang).asString());
                assertThat(faqs.get(i).get("answer").asString()).isEqualTo(expectedFaqs.get(i).get("answer").get(lang).asString());
                assertThat(faqs.get(i).get("ruleCode").asString()).isEqualTo(expectedFaqs.get(i).get("rule").asString());
                assertThat(faqs.get(i).get("language").asString()).isEqualTo(lang);
            }
        }

        // FE 키로도 거른다
        JsonNode waste = data(get("/api/rules").param("category", "waste").param("language", "en"));
        assertThat(waste.size()).isEqualTo(4);
        waste.forEach(rule -> assertThat(rule.get("categoryKey").asString()).isEqualTo("waste"));

        // 상세는 슬러그 주소 그대로
        mockMvc.perform(get("/api/rules/order-noise").param("language", "vi").cookie(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categoryKey").value("order"))
                .andExpect(jsonPath("$.data.guideId").isNumber());
    }

    @Test
    void reportsAcceptGuideCodeForRulesChecklistAndLifeChangeTasks() throws Exception {
        for (String code : List.of("waste-food", "CL-01", "LC-01-1")) {
            mockMvc.perform(post("/api/reports").with(csrf()).cookie(member)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"guideCode\":\"" + code + "\",\"quotedText\":\"문장\",\"reason\":\"CONTENT\"}"))
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(jsonPath("$.data.targetCode").value(code));
        }
        mockMvc.perform(post("/api/reports").with(csrf()).cookie(member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guideCode\":\"no-such-rule\",\"quotedText\":\"문장\",\"reason\":\"CONTENT\"}"))
                .andExpect(status().isNotFound());
    }

    /** FE #73: 공식 근거를 확인해 BE 에서 더한 항목. 4개 언어, 출처 목록, 확인일, 분류 안 순서 */
    @Test
    void rulesAddedAfterFeMigrationHaveSourcesAndCheckedDate() throws Exception {
        for (String lang : LANGUAGES) {
            JsonNode order = data(get("/api/rules").param("category", "order").param("language", lang));
            List<String> orderCodes = new ArrayList<>();
            order.forEach(rule -> orderCodes.add(rule.get("code").asString()));
            assertThat(orderCodes).as(lang).containsSubsequence("order-noise", "order-smoking", "order-etiquette");

            JsonNode living = data(get("/api/rules").param("category", "living").param("language", lang));
            List<String> livingCodes = new ArrayList<>();
            living.forEach(rule -> livingCodes.add(rule.get("code").asString()));
            assertThat(livingCodes).as(lang).containsSubsequence("living-siru", "living-bike", "living-youth-transport");

            for (String code : List.of("order-etiquette", "living-youth-transport")) {
                JsonNode rule = data(get("/api/rules/" + code).param("language", lang));
                assertThat(rule.get("translated").asBoolean()).as(code + " " + lang).isTrue();
                assertThat(rule.get("points").size()).as(code + " " + lang).isEqualTo(6);
                assertThat(rule.get("checkedOn").asString()).isEqualTo("2026-11-27");
                assertThat(rule.get("sources").size()).isGreaterThanOrEqualTo(2);
                rule.get("sources").forEach(source ->
                        assertThat(source.get("url").asString()).startsWith("https://"));
            }
        }
        // 범칙금액은 시행령 별표 그대로
        JsonNode etiquette = data(get("/api/rules/order-etiquette").param("language", "ko"));
        assertThat(etiquette.get("points").get(0).asString()).contains("5만원").contains("3만원");
        assertThat(etiquette.get("law").asString()).contains("경범죄 처벌법");
    }

    @Test
    void seededTranslationsAreReviewedSoAutoTranslationKeepsThem() {
        Integer notReviewed = jdbc.queryForObject("SELECT count(*) FROM guide_translation t JOIN guide g "
                + "ON g.guide_id = t.guide_id WHERE g.kind = 'RULE' AND g.code = ANY(?) AND NOT t.reviewed",
                Integer.class, (Object) codes());
        assertThat(notReviewed).isZero();
    }

    private String[] codes() {
        List<String> codes = new ArrayList<>();
        fe.get("rules").forEach(rule -> codes.add(rule.get("code").asString()));
        return codes.toArray(String[]::new);
    }

    private JsonNode data(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request.cookie(member)).andExpect(status().isOk()).andReturn();
        return jsonMapper.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).get("data");
    }

    private static JsonNode find(JsonNode rules, String code) {
        for (JsonNode rule : rules) {
            if (code.equals(rule.get("code").asString())) {
                return rule;
            }
        }
        throw new AssertionError("rule " + code + " not in API response");
    }

    private Cookie signup(String email) throws Exception {
        redisTemplate.opsForValue().set(SignupVerificationService.VERIFIED_KEY + email, "1");
        MvcResult result = mockMvc.perform(post("/api/auth/signup").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"abcd1234\",\"passwordConfirm\":\"abcd1234\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return result.getResponse().getCookie("SIOT_SESSION");
    }
}
