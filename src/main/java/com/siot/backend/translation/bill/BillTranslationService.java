package com.siot.backend.translation.bill;

import com.siot.backend.checklist.domain.Guide;
import com.siot.backend.checklist.domain.GuideTranslation;
import com.siot.backend.checklist.repository.GuideRepository;
import com.siot.backend.checklist.repository.GuideTranslationRepository;
import com.siot.backend.global.common.RequestRateLimiter;
import com.siot.backend.global.common.SupportedLanguages;
import com.siot.backend.global.exception.BusinessException;
import com.siot.backend.global.exception.ErrorCode;
import com.siot.backend.member.repository.AppUserRepository;
import com.siot.backend.rag.ai.AiUnavailableException;
import com.siot.backend.translation.BillReader;
import com.siot.backend.translation.BillReading;
import com.siot.backend.translation.config.TranslationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * RUL-07 고지서 사진 번역. 사진은 메모리에서만 다루고 처리 후 바로 지운다 (저장·알림 없음).
 * 로그에는 요청 id, 크기, 형식, 처리 시간, 결과 상태만 남긴다 (사진·글자·금액·이름·주소·납세번호는 남기지 않는다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillTranslationService {

    static final String RATE_LIMIT_KEY = "bill-translation:";

    private final BillReader billReader;
    private final RequestRateLimiter rateLimiter;
    private final TranslationProperties properties;
    private final AppUserRepository userRepository;
    private final GuideRepository guideRepository;
    private final GuideTranslationRepository guideTranslationRepository;
    private final Clock clock;

    public BillTranslationResponse translate(Long userId, MultipartFile file, boolean consent, String language) {
        long started = System.nanoTime();
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        long size = file == null ? 0 : file.getSize();
        String type = "-";
        String result = "ERROR";
        byte[] bytes = null;
        byte[] image = null;
        try {
            if (!consent) {
                throw new BusinessException(ErrorCode.BILL_CONSENT_REQUIRED);
            }
            String lang = language(userId, language);
            if (file == null || file.isEmpty()) {
                throw new BusinessException(ErrorCode.BILL_FILE_INVALID);
            }
            if (size > properties.billMaxFileSize().toBytes()) {
                throw new BusinessException(ErrorCode.BILL_FILE_TOO_LARGE);
            }
            bytes = bytes(file);
            type = BillFileValidator.detect(file.getContentType(), bytes);
            acquire(userId);

            int pageCount = 1;
            String imageType = type;
            if (BillFileValidator.PDF.equals(type)) {
                PdfFirstPage.Rendered rendered = PdfFirstPage.render(bytes);
                image = rendered.png();
                imageType = BillFileValidator.PNG;
                pageCount = rendered.pageCount();
            } else {
                image = bytes;
            }

            BillReading reading;
            try {
                reading = billReader.read(image, imageType, lang);
            } catch (AiUnavailableException e) {
                // 메시지는 상태 코드만 담는다 (GeminiBillReader)
                result = "AI_UNAVAILABLE (" + e.getMessage() + ")";
                throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
            }
            BillTranslationResponse response = response(reading, lang, pageCount);
            result = !response.readable() ? "UNREADABLE " + response.retake().reason()
                    : "OK summary=" + response.summarySource();
            return response;
        } catch (BusinessException e) {
            if ("ERROR".equals(result)) {
                result = e.getErrorCode().name();
            }
            throw e;
        } finally {
            wipe(bytes);
            wipe(image);
            log.info("Bill translation id={} size={} type={} ms={} result={}", requestId, size, type,
                    (System.nanoTime() - started) / 1_000_000, result);
        }
    }

    BillTranslationResponse response(BillReading reading, String language, int pageCount) {
        boolean firstPageOnly = pageCount > 1;
        if (!reading.readable()) {
            String reason = reading.retakeReason() == null ? BillReading.BLURRY : reading.retakeReason();
            return new BillTranslationResponse(language, false,
                    new BillTranslationResponse.Retake(reason, BillMessages.retake(reason, language)),
                    null, null, null, null, null, null, null, null, null, List.of(), null, List.of(),
                    null, null, List.of(), List.of(), firstPageOnly, pageCount);
        }
        boolean aiSummary = BillSummaryCheck.consistent(reading);
        BillReading.Summary summary = aiSummary ? reading.summary() : BillMessages.summary(reading, language);
        List<BillTranslationResponse.Warning> warnings = BillWarnings.of(reading, language, LocalDate.now(clock))
                .stream()
                .map(warning -> new BillTranslationResponse.Warning(warning.code(), warning.message()))
                .toList();
        return new BillTranslationResponse(
                language,
                true,
                null,
                reading.documentType(),
                reading.issuer(),
                reading.taxType(),
                BillMasker.mask(reading.taxObject()),
                reading.amount(),
                reading.dueDate(),
                reading.lateAmount(),
                reading.lateDueDate(),
                reading.penalty(),
                BillMasker.mask(reading.paymentMethods()),
                reading.contact(),
                BillMasker.mask(reading.originalLines()),
                new BillTranslationResponse.Summary(BillMasker.mask(summary.what()), BillMasker.mask(summary.due()),
                        BillMasker.mask(summary.amount()), BillMasker.mask(summary.howToPay())),
                aiSummary ? "AI" : "SERVER",
                warnings,
                guides(BillGuideLinks.codes(reading.taxType()), language),
                firstPageOnly,
                pageCount);
    }

    private String language(Long userId, String requested) {
        if (requested != null && !requested.isBlank()) {
            if (!SupportedLanguages.isSupported(requested)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "language: ko, en, zh, vi 중 하나로 보내 주세요.");
            }
            return requested;
        }
        return userRepository.findById(userId)
                .map(user -> SupportedLanguages.orDefault(user.getLanguage()))
                .orElse(SupportedLanguages.DEFAULT);
    }

    private void acquire(Long userId) {
        TranslationProperties.RateLimit limit = properties.billRateLimit();
        RequestRateLimiter.Result acquired = rateLimiter.tryAcquire(RATE_LIMIT_KEY + userId,
                limit.minInterval(), limit.maxRequests(), limit.window());
        switch (acquired) {
            case TOO_FREQUENT -> throw new BusinessException(ErrorCode.BILL_TRANSLATION_TOO_FREQUENT);
            case LIMIT_EXCEEDED -> throw new BusinessException(ErrorCode.BILL_TRANSLATION_LIMIT);
            case ALLOWED -> {
            }
        }
    }

    private List<BillTranslationResponse.RelatedGuide> guides(List<String> codes, String language) {
        if (codes.isEmpty()) {
            return List.of();
        }
        Map<String, Guide> byCode = guideRepository.findByCodeIn(codes).stream()
                .filter(guide -> !guide.isDeleted())
                .collect(Collectors.toMap(Guide::getCode, Function.identity()));
        return codes.stream()
                .filter(byCode::containsKey)
                .map(byCode::get)
                .map(guide -> new BillTranslationResponse.RelatedGuide(guide.getCode(), guide.getKind(),
                        title(guide, language)))
                .toList();
    }

    private String title(Guide guide, String language) {
        return guideTranslationRepository.findByGuideIdAndLanguage(guide.getId(), language)
                .or(() -> guideTranslationRepository.findByGuideIdAndLanguage(guide.getId(), SupportedLanguages.DEFAULT))
                .map(GuideTranslation::getTitle)
                .orElse(null);
    }

    private static byte[] bytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.BILL_FILE_INVALID);
        }
    }

    private static void wipe(byte[] data) {
        if (data != null) {
            Arrays.fill(data, (byte) 0);
        }
    }
}
