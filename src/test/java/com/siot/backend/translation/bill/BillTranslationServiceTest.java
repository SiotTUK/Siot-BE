package com.siot.backend.translation.bill;

import com.siot.backend.checklist.repository.GuideRepository;
import com.siot.backend.checklist.repository.GuideTranslationRepository;
import com.siot.backend.global.common.RequestRateLimiter;
import com.siot.backend.global.exception.BusinessException;
import com.siot.backend.global.exception.ErrorCode;
import com.siot.backend.member.repository.AppUserRepository;
import com.siot.backend.rag.ai.AiUnavailableException;
import com.siot.backend.translation.BillReader;
import com.siot.backend.translation.BillReading;
import com.siot.backend.translation.config.TranslationProperties;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** RUL-07 처리 흐름 (BillReader·Redis 모킹). 통합 테스트는 BillTranslationIntegrationTest */
@ExtendWith(OutputCaptureExtension.class)
class BillTranslationServiceTest {

    private static final long USER_ID = 7L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-21T03:00:00Z"), ZoneId.of("Asia/Seoul"));

    private final BillReader billReader = mock(BillReader.class);
    private final RequestRateLimiter rateLimiter = mock(RequestRateLimiter.class);
    private final AppUserRepository userRepository = mock(AppUserRepository.class);
    private final GuideRepository guideRepository = mock(GuideRepository.class);
    private final GuideTranslationRepository guideTranslationRepository = mock(GuideTranslationRepository.class);
    private final TranslationProperties properties = new TranslationProperties(null, Duration.ofSeconds(45), DataSize.ofMegabytes(10), Duration.ZERO,
            new TranslationProperties.RateLimit(10, Duration.ofHours(1), Duration.ofSeconds(2)));
    private final BillTranslationService service = new BillTranslationService(billReader, rateLimiter, properties,
            userRepository, guideRepository, guideTranslationRepository, CLOCK);

    @BeforeEach
    void setUp() {
        when(rateLimiter.tryAcquire(anyString(), any(), anyInt(), any())).thenReturn(RequestRateLimiter.Result.ALLOWED);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(guideRepository.findByCodeIn(any())).thenReturn(List.of());
    }

    @Test
    void requiresConsentBeforeAnything() {
        assertThatThrownBy(() -> service.translate(USER_ID, jpeg(), false, "en"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BILL_CONSENT_REQUIRED);
        verifyNoInteractions(billReader, rateLimiter);
    }

    @Test
    void rejectsUnsupportedLanguage() {
        assertThatThrownBy(() -> service.translate(USER_ID, jpeg(), true, "ja"))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void rejectsWrongFormatAndDoesNotCountIt() {
        // 이름은 .jpg 인데 내용은 GIF
        MockMultipartFile gif = new MockMultipartFile("file", "bill.jpg", "image/jpeg", "GIF89a....".getBytes());
        // 내용은 PDF 인데 image/png 로 보냄
        MockMultipartFile pdfAsPng = new MockMultipartFile("file", "bill.png", "image/png", "%PDF-1.7 ...".getBytes());
        MockMultipartFile empty = new MockMultipartFile("file", "bill.jpg", "image/jpeg", new byte[0]);

        for (MockMultipartFile file : List.of(gif, pdfAsPng, empty)) {
            assertThatThrownBy(() -> service.translate(USER_ID, file, true, "en"))
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.BILL_FILE_INVALID);
        }
        assertThatThrownBy(() -> service.translate(USER_ID, null, true, "en"))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BILL_FILE_INVALID);
        verifyNoInteractions(billReader, rateLimiter);
    }

    @Test
    void rejectsFileOverTenMegabytes() {
        byte[] big = new byte[(int) DataSize.ofMegabytes(10).toBytes() + 1];
        big[0] = (byte) 0xFF;
        big[1] = (byte) 0xD8;
        big[2] = (byte) 0xFF;

        assertThatThrownBy(() -> service.translate(USER_ID,
                new MockMultipartFile("file", "bill.jpg", "image/jpeg", big), true, "en"))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BILL_FILE_TOO_LARGE);
        verifyNoInteractions(billReader);
    }

    @Test
    void rateLimitExceededIs429() {
        when(rateLimiter.tryAcquire(eq("bill-translation:7"), eq(Duration.ofSeconds(2)), eq(10), eq(Duration.ofHours(1))))
                .thenReturn(RequestRateLimiter.Result.LIMIT_EXCEEDED);

        assertThatThrownBy(() -> service.translate(USER_ID, jpeg(), true, "en"))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BILL_TRANSLATION_LIMIT);
        assertThat(ErrorCode.BILL_TRANSLATION_LIMIT.getStatus().value()).isEqualTo(429);
        verifyNoInteractions(billReader);
    }

    @Test
    void aiFailureIs503() {
        when(billReader.read(any(), anyString(), anyString())).thenThrow(new AiUnavailableException("Gemini bill reading failed (429)"));

        assertThatThrownBy(() -> service.translate(USER_ID, jpeg(), true, "en"))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_UNAVAILABLE);
        assertThat(ErrorCode.AI_UNAVAILABLE.getStatus().value()).isEqualTo(503);
    }

    @Test
    void unreadablePhotoAsksToRetake() {
        when(billReader.read(any(), anyString(), anyString())).thenReturn(BillReading.unreadable("BLURRY"));

        BillTranslationResponse response = service.translate(USER_ID, jpeg(), true, "zh");

        assertThat(response.readable()).isFalse();
        assertThat(response.retake().reason()).isEqualTo("BLURRY");
        assertThat(response.retake().message()).isEqualTo(BillMessages.retake("BLURRY", "zh"));
        assertThat(response.summary()).isNull();
        assertThat(response.warnings()).isEmpty();
        assertThat(response.relatedGuides()).isEmpty();
    }

    @Test
    void keepsAiSummaryWhenAmountAndDateMatch() {
        when(billReader.read(any(), eq("image/jpeg"), eq("en"))).thenReturn(reading(
                new BillReading.Summary("Car tax notice", "Pay by 2026-12-31.", "Pay 123,450 won.", "Wetax")));

        BillTranslationResponse response = service.translate(USER_ID, jpeg(), true, "en");

        assertThat(response.summarySource()).isEqualTo("AI");
        assertThat(response.summary().amount()).isEqualTo("Pay 123,450 won.");
    }

    /** 요구: 요약의 금액·날짜가 amount·dueDate 와 다르면 요약을 버리고 서버가 amount·dueDate 로 만든다 */
    @Test
    void replacesAiSummaryWhenAmountOrDateDiffers() {
        when(billReader.read(any(), anyString(), anyString())).thenReturn(reading(
                new BillReading.Summary("Car tax notice", "Pay by 2026-12-30.", "Pay 132,450 won.", "Wetax")));

        BillTranslationResponse response = service.translate(USER_ID, jpeg(), true, "en");

        assertThat(response.summarySource()).isEqualTo("SERVER");
        assertThat(response.summary().due()).isEqualTo("Pay by: 2026-12-31");
        assertThat(response.summary().amount()).isEqualTo("Amount to pay: 123,450 won");
        assertThat(response.summary().what()).isEqualTo("Tax notice - 자동차세 (시흥시)");
        assertThat(response.amount()).isEqualTo(123450L);
        assertThat(response.dueDate()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    void masksPersonalDataTheModelMissed() {
        when(billReader.read(any(), anyString(), anyString())).thenReturn(new BillReading(true, null, "TAX_NOTICE",
                "시흥시", "자동차세", "12가3456", 123450L, LocalDate.of(2026, 12, 31), null, null, null,
                List.of("가상계좌 79012345678901"), "031-310-2114",
                List.of("납세자 주소 시흥시 정왕동 1234-5", "주민번호 900101-1234567"), null));

        BillTranslationResponse response = service.translate(USER_ID, jpeg(), true, "ko");

        assertThat(response.taxObject()).isEqualTo("12가****");
        assertThat(response.originalLines()).containsExactly("납세자 주소 시흥시 정왕동 ***", "주민번호 ******-*******");
        assertThat(response.paymentMethods()).containsExactly("가상계좌 **************");
        assertThat(response.contact()).isEqualTo("031-310-2114");
    }

    @Test
    void usesMemberLanguageWhenNotGiven() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(billReader.read(any(), anyString(), eq("ko"))).thenReturn(BillReading.unreadable("NOT_A_BILL"));

        assertThat(service.translate(USER_ID, jpeg(), true, null).language()).isEqualTo("ko");
    }

    @Test
    void readsOnlyFirstPageOfPdf() throws IOException {
        when(billReader.read(any(), eq("image/png"), anyString())).thenReturn(BillReading.unreadable("NOT_A_BILL"));

        BillTranslationResponse response = service.translate(USER_ID,
                new MockMultipartFile("file", "bill.pdf", "application/pdf", pdf(3)), true, "en");

        assertThat(response.firstPageOnly()).isTrue();
        assertThat(response.pageCount()).isEqualTo(3);
        verify(billReader).read(any(), eq("image/png"), eq("en"));
    }

    @Test
    void brokenPdfIsInvalidFile() {
        assertThatThrownBy(() -> service.translate(USER_ID,
                new MockMultipartFile("file", "bill.pdf", "application/pdf", "%PDF-1.7 broken".getBytes()), true, "en"))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BILL_FILE_INVALID);
    }

    /** 로그에는 요청 id·크기·형식·처리 시간·결과만. 금액·이름·주소·번호는 남기지 않는다 */
    @Test
    void logsDoNotContainBillContents(CapturedOutput output) {
        when(billReader.read(any(), anyString(), anyString())).thenReturn(new BillReading(true, null, "DEMAND_NOTICE",
                "시흥시", "자동차세", "12가3456", 987654L, LocalDate.of(2026, 12, 31), null, null, 29629L,
                List.of("Wetax"), "031-310-2114", List.of("납세자 홍길동", "시흥시 정왕동 1234-5"),
                new BillReading.Summary("홍길동 님의 독촉장", "2026-12-31", "987,654원", "위택스")));

        service.translate(USER_ID, jpeg(), true, "ko");
        when(billReader.read(any(), anyString(), anyString())).thenThrow(new AiUnavailableException("Gemini bill reading failed (503)"));
        assertThatThrownBy(() -> service.translate(USER_ID, jpeg(), true, "ko")).isInstanceOf(BusinessException.class);

        assertThat(output.getAll())
                .contains("Bill translation id=", "result=OK summary=AI", "result=AI_UNAVAILABLE")
                .doesNotContain("홍길동", "987654", "987,654", "29629", "12가", "정왕동", "1234-5", "2026-12-31");
    }

    private static MockMultipartFile jpeg() {
        // 처리 후 바이트를 지우므로 매번 새로 만든다
        return new MockMultipartFile("file", "bill.jpg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10});
    }

    private static BillReading reading(BillReading.Summary summary) {
        return new BillReading(true, null, "TAX_NOTICE", "시흥시", "자동차세", "12가****", 123450L,
                LocalDate.of(2026, 12, 31), null, null, null, List.of("Wetax (위택스)"), "031-310-2114",
                List.of("납기내 금액 123,450원"), summary);
    }

    private static byte[] pdf(int pages) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) {
                document.addPage(new PDPage());
            }
            document.save(out);
            return out.toByteArray();
        }
    }
}
