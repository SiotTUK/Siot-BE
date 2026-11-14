package com.siot.backend.auth.service;

import com.siot.backend.auth.domain.CredentialPolicy;
import com.siot.backend.auth.dto.SignupVerificationResponse;
import com.siot.backend.global.common.RequestRateLimiter;
import com.siot.backend.global.common.VerificationCodeStore;
import com.siot.backend.global.exception.BusinessException;
import com.siot.backend.global.exception.ErrorCode;
import com.siot.backend.global.mail.EmailSender;
import com.siot.backend.global.mail.MailTemplates;
import com.siot.backend.member.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Set;

/**
 * M-01: 가입 인증 코드. 이메일(아이디)로 코드를 보내고, 코드를 확인한 이메일만 가입할 수 있다.
 * <ol>
 *   <li>코드 요청: 형식·중복 검사 → 6자리 코드 메일 발송 (10분 유효)</li>
 *   <li>코드 확인: 맞으면 그 이메일을 30분 동안 "가입 가능" 상태로 둔다</li>
 *   <li>가입(AuthService): 가입 가능 상태인 이메일만 허용하고, 가입하면 상태를 지운다</li>
 * </ol>
 * 계정이 생기기 전이라 요청 제한은 이메일 기준이다 (1분에 1회, 하루 10회).
 */
@Service
@RequiredArgsConstructor
public class SignupVerificationService {

    static final Duration CODE_TTL = Duration.ofMinutes(10);
    static final int MAX_ATTEMPTS = 5;
    static final Duration RESEND_INTERVAL = Duration.ofMinutes(1);
    static final int DAILY_LIMIT = 10;
    /** 코드를 확인한 뒤 가입을 마쳐야 하는 시간 */
    static final Duration VERIFIED_TTL = Duration.ofMinutes(30);

    private static final String NAMESPACE = "signup-verification";
    /** 코드를 확인한 이메일 (값: "1") */
    public static final String VERIFIED_KEY = "siot:signup-verified:";
    private static final Set<String> LANGUAGES = Set.of("ko", "en", "zh", "vi");

    private final AppUserRepository appUserRepository;
    private final VerificationCodeStore codeStore;
    private final RequestRateLimiter rateLimiter;
    private final StringRedisTemplate redisTemplate;
    private final EmailSender emailSender;
    private final MailTemplates mailTemplates;

    /** @param language 메일 문구 언어 (가입 화면 언어, 없으면 ko) */
    @Transactional(readOnly = true)
    public SignupVerificationResponse requestCode(String rawEmail, String language) {
        String email = CredentialPolicy.normalizeEmail(rawEmail);
        CredentialPolicy.validateEmail(email);
        if (appUserRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        switch (rateLimiter.tryAcquire(NAMESPACE + ":" + email, RESEND_INTERVAL, DAILY_LIMIT, Duration.ofDays(1))) {
            case TOO_FREQUENT -> throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_TOO_FREQUENT);
            case LIMIT_EXCEEDED -> throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_DAILY_LIMIT);
            case ALLOWED -> { }
        }

        String code = codeStore.issue(NAMESPACE, email, email, CODE_TTL);
        String mailLanguage = language != null && LANGUAGES.contains(language) ? language : "ko";
        emailSender.send(mailTemplates.emailVerification(email, mailLanguage, code, CODE_TTL.toMinutes()));
        return new SignupVerificationResponse(email, CODE_TTL.toSeconds(), RESEND_INTERVAL.toSeconds());
    }

    /** 코드가 맞으면 30분 안에 가입할 수 있다. 5회 틀리면 코드를 폐기한다. */
    public void confirmCode(String rawEmail, String code) {
        String email = CredentialPolicy.normalizeEmail(rawEmail);
        switch (codeStore.verify(NAMESPACE, email, email, code, MAX_ATTEMPTS)) {
            case INVALID -> throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_CODE_INVALID);
            case EXPIRED -> throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_EXPIRED);
            case VERIFIED -> redisTemplate.opsForValue().set(VERIFIED_KEY + email, "1", VERIFIED_TTL);
        }
    }

    /** @param email 정규화한 이메일 */
    public boolean isVerified(String email) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(VERIFIED_KEY + email));
    }

    /** 가입이 끝나면 호출한다 (한 번 확인한 코드로 한 번만 가입) */
    public void consume(String email) {
        redisTemplate.delete(VERIFIED_KEY + email);
    }
}
