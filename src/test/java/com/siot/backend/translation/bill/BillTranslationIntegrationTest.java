package com.siot.backend.translation.bill;

import com.siot.backend.TestcontainersConfiguration;
import com.siot.backend.auth.service.SignupVerificationService;
import com.siot.backend.global.exception.GlobalExceptionHandler;
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

    /** application.yml 기본값: 전용 모델은 비어 있어 채팅 모델을 쓰고, 제한 시간은 45초 */
    @Test
    void billModelDefaultsToChatModel(@Autowired com.siot.backend.translation.config.TranslationProperties properties,
                                      @Autowired com.siot.backend.rag.config.GeminiProperties gemini) {
        assertThat(properties.billModelOr(gemini.chatModel())).isEqualTo(gemini.chatModel());
        assertThat(properties.billTimeout()).isEqualTo(java.time.Duration.ofSeconds(45));
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
