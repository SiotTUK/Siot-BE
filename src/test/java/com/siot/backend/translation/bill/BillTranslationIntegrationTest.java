package com.siot.backend.translation.bill;

import com.siot.backend.TestcontainersConfiguration;
import com.siot.backend.auth.service.SignupVerificationService;
import com.siot.backend.global.exception.GlobalExceptionHandler;
import com.siot.backend.schedule.domain.CalendarSchedule;
import com.siot.backend.schedule.repository.CalendarScheduleRepository;
import com.siot.backend.translation.BillReader;
import com.siot.backend.translation.BillReading;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** RUL-07 POST /api/translations/bills (BillReader 모킹, DB·Redis 는 Testcontainers) */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BillTranslationIntegrationTest {

    private static final String EMAIL = "bill-member@example.com";
    private static final String PASSWORD = "abcd1234";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    CalendarScheduleRepository schedules;

    @MockitoBean
    BillReader billReader;

    private Cookie member;
    private long memberId;

    @BeforeEach
    void setUp() throws Exception {
        reset(billReader);
        member = login();
        memberId = jdbcTemplate.queryForObject("SELECT user_id FROM app_user WHERE email = ?", Long.class, EMAIL);
        redisTemplate.delete(List.of(
                "siot:rate:" + BillTranslationService.RATE_LIMIT_KEY + memberId + ":interval",
                "siot:rate:" + BillTranslationService.RATE_LIMIT_KEY + memberId + ":count"));
    }

    @Test
    void translatesBillWithWarningsAndRelatedGuide() throws Exception {
        jdbcTemplate.update("UPDATE app_user SET language = 'en' WHERE user_id = ?", memberId);
        when(billReader.read(any(), eq("image/jpeg"), eq("en"))).thenReturn(new BillReading(true, null,
                "DEMAND_NOTICE", "시흥시", "자동차세", "12가3456", 123450L, LocalDate.of(2099, 12, 31), null, null,
                3700L, List.of("Wetax (위택스)"), "031-310-2114", List.of("납기내 금액 123,450원"),
                new BillReading.Summary("Demand notice for car tax", "Pay by 2099-12-31.", "Pay 123,450 won.", "Wetax")));

        mockMvc.perform(multipart("/api/translations/bills").file(jpeg()).param("consent", "true")
                        .cookie(member).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.language").value("en"))
                .andExpect(jsonPath("$.data.readable").value(true))
                .andExpect(jsonPath("$.data.documentType").value("DEMAND_NOTICE"))
                .andExpect(jsonPath("$.data.taxObject").value("12가****"))
                .andExpect(jsonPath("$.data.amount").value(123450))
                .andExpect(jsonPath("$.data.dueDate").value("2099-12-31"))
                .andExpect(jsonPath("$.data.summarySource").value("AI"))
                .andExpect(jsonPath("$.data.warnings[*].code").value(
                        org.hamcrest.Matchers.contains("LATE_PENALTY", "SEIZURE", "STAY_EXTENSION")))
                .andExpect(jsonPath("$.data.relatedGuides[0].code").value("CL-10"))
                .andExpect(jsonPath("$.data.relatedGuides[0].kind").value("CHECKLIST"))
                .andExpect(jsonPath("$.data.relatedGuides[0].title").isNotEmpty())
                .andExpect(jsonPath("$.data.firstPageOnly").value(false))
                .andExpect(jsonPath("$.data.pageCount").value(1));
    }

    /** CAL-03 세금 상세 보기: 세목 → taxKind, 납기가 든 공통 세금 일정 → taxScheduleId. 저장한 결과를 다시 볼 때도 같다 */
    @Test
    void linksTaxBillToTheCommonTaxScheduleOfItsDueDate() throws Exception {
        // 다른 테스트의 2099년 일정과 겹치지 않게 2097년
        CalendarSchedule june = schedules.saveAndFlush(CalendarSchedule.create(null, "TAX_CAR",
                LocalDate.of(2097, 6, 16), LocalDate.of(2097, 6, 30), null, null, true, null));
        when(billReader.read(any(), eq("image/jpeg"), anyString())).thenReturn(new BillReading(true, null,
                "TAX_NOTICE", "시흥시", "자동차세 (1기분)", "12가3456", 123450L, LocalDate.of(2097, 6, 30), null, null,
                null, List.of(), null, List.of("자동차세 123,450원"),
                new BillReading.Summary("Car tax", "Pay by 2097-06-30.", "Pay 123,450 won.", "Wetax")));

        MvcResult result = mockMvc.perform(multipart("/api/translations/bills").file(jpeg())
                        .param("consent", "true").cookie(member).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taxKind").value("TAX_CAR"))
                .andExpect(jsonPath("$.data.taxScheduleId").value(june.getId()))
                .andReturn();
        Number id = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(get("/api/translations/bills/" + id).cookie(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taxKind").value("TAX_CAR"))
                .andExpect(jsonPath("$.data.taxScheduleId").value(june.getId()));
    }

    /** 세금이 아닌 고지서, 또는 납기가 든 공통 일정이 없으면 연결하지 않는다 (종류만 안다) */
    @Test
    void leavesTaxScheduleEmptyWithoutMatchingSchedule() throws Exception {
        when(billReader.read(any(), eq("image/jpeg"), anyString())).thenReturn(new BillReading(true, null,
                "TAX_NOTICE", "시흥시", "재산세", null, 50000L, LocalDate.of(2096, 1, 5), null, null,
                null, List.of(), null, List.of("재산세 50,000원"),
                new BillReading.Summary("Property tax", "Pay by 2096-01-05.", "Pay 50,000 won.", "Wetax")));
        mockMvc.perform(multipart("/api/translations/bills").file(jpeg()).param("consent", "true")
                        .cookie(member).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taxKind").value("TAX_PROPERTY"))
                .andExpect(jsonPath("$.data.taxScheduleId").doesNotExist());

        when(billReader.read(any(), eq("image/jpeg"), anyString())).thenReturn(new BillReading(true, null,
                "UTILITY", "시흥시", "상수도요금", null, 20000L, LocalDate.of(2096, 1, 5), null, null,
                null, List.of(), null, List.of("상수도요금 20,000원"),
                new BillReading.Summary("Water bill", "Pay by 2096-01-05.", "Pay 20,000 won.", "Bank")));
        redisTemplate.delete("siot:rate:" + BillTranslationService.RATE_LIMIT_KEY + memberId + ":interval");
        mockMvc.perform(multipart("/api/translations/bills").file(jpeg()).param("consent", "true")
                        .cookie(member).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taxKind").doesNotExist())
                .andExpect(jsonPath("$.data.taxScheduleId").doesNotExist());
    }

    @Test
    void requiresLogin() throws Exception {
        mockMvc.perform(multipart("/api/translations/bills").file(jpeg()).param("consent", "true").with(csrf()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(billReader);
    }

    @Test
    void requiresConsent() throws Exception {
        mockMvc.perform(multipart("/api/translations/bills").file(jpeg()).cookie(member).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BILL_CONSENT_REQUIRED"));
        mockMvc.perform(multipart("/api/translations/bills").file(jpeg()).param("consent", "false")
                        .cookie(member).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BILL_CONSENT_REQUIRED"));
        verifyNoInteractions(billReader);
    }

    @Test
    void rejectsWrongFormatAndLargeFile() throws Exception {
        mockMvc.perform(multipart("/api/translations/bills")
                        .file(new MockMultipartFile("file", "bill.gif", "image/gif", "GIF89a....".getBytes()))
                        .param("consent", "true").cookie(member).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BILL_FILE_INVALID"));

        byte[] big = new byte[10 * 1024 * 1024 + 1];
        big[0] = (byte) 0xFF;
        big[1] = (byte) 0xD8;
        big[2] = (byte) 0xFF;
        mockMvc.perform(multipart("/api/translations/bills")
                        .file(new MockMultipartFile("file", "bill.jpg", "image/jpeg", big))
                        .param("consent", "true").cookie(member).with(csrf()))
                .andExpect(status().is(413))
                .andExpect(jsonPath("$.code").value("BILL_FILE_TOO_LARGE"));
        verifyNoInteractions(billReader);
    }

    @Test
    void unreadablePhotoReturnsRetakeGuide() throws Exception {
        when(billReader.read(any(), anyString(), eq("vi"))).thenReturn(BillReading.unreadable("CROPPED"));

        mockMvc.perform(multipart("/api/translations/bills").file(jpeg()).param("consent", "true")
                        .param("language", "vi").cookie(member).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readable").value(false))
                .andExpect(jsonPath("$.data.retake.reason").value("CROPPED"))
                .andExpect(jsonPath("$.data.retake.message").value(BillMessages.retake("CROPPED", "vi")))
                .andExpect(jsonPath("$.data.summary").doesNotExist());
    }

    @Test
    void hourlyLimitReturns429() throws Exception {
        redisTemplate.opsForValue().set("siot:rate:" + BillTranslationService.RATE_LIMIT_KEY + memberId + ":count", "10");

        mockMvc.perform(multipart("/api/translations/bills").file(jpeg()).param("consent", "true")
                        .cookie(member).with(csrf()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("BILL_TRANSLATION_LIMIT"))
                .andExpect(jsonPath("$.message").isNotEmpty());
        verifyNoInteractions(billReader);
    }

    @Test
    void aiUnavailableReturns503() throws Exception {
        when(billReader.read(any(), anyString(), anyString()))
                .thenThrow(new com.siot.backend.rag.ai.AiUnavailableException("Gemini bill reading failed (503)"));

        mockMvc.perform(multipart("/api/translations/bills").file(jpeg()).param("consent", "true")
                        .cookie(member).with(csrf()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_UNAVAILABLE"));
    }

    /** application.yml 기본값: 전용 모델은 비어 있어 채팅 모델을 쓰고, 제한 시간은 75초 */
    @Test
    void billModelDefaultsToChatModel(@Autowired com.siot.backend.translation.config.TranslationProperties properties,
                                      @Autowired com.siot.backend.rag.config.GeminiProperties gemini) {
        assertThat(properties.billModelOr(gemini.chatModel())).isEqualTo(gemini.chatModel());
        assertThat(properties.billTimeout()).isEqualTo(java.time.Duration.ofSeconds(75));
    }

    /** multipart 한도(30MB)를 넘은 요청: 고지서 경로면 고지서 문구, 나머지는 문서 문구 */
    @Test
    void multipartLimitUsesBillCodeOnBillPath() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest bill = new MockHttpServletRequest("POST", "/api/translations/bills");
        MockHttpServletRequest document = new MockHttpServletRequest("POST", "/api/admin/documents");

        assertThat(handler.handleMaxUploadSize(new MaxUploadSizeExceededException(1), bill).getBody().code())
                .isEqualTo("BILL_FILE_TOO_LARGE");
        assertThat(handler.handleMaxUploadSize(new MaxUploadSizeExceededException(1), document).getBody().code())
                .isEqualTo("DOCUMENT_FILE_TOO_LARGE");
    }

    private static MockMultipartFile jpeg() {
        return new MockMultipartFile("file", "bill.jpg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10});
    }

    private Cookie login() throws Exception {
        Integer exists = jdbcTemplate.queryForObject("SELECT count(*) FROM app_user WHERE email = ?", Integer.class, EMAIL);
        if (exists == 0) {
            redisTemplate.opsForValue().set(SignupVerificationService.VERIFIED_KEY + EMAIL, "1");
            mockMvc.perform(post("/api/auth/signup").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD
                                    + "\",\"passwordConfirm\":\"" + PASSWORD + "\"}"))
                    .andExpect(status().isCreated());
        }
        MvcResult result = mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie("SIOT_SESSION");
    }
}
