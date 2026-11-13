package com.siot.backend.member.service;

import com.siot.backend.global.common.RequestRateLimiter;
import com.siot.backend.global.common.TokenUtils;
import com.siot.backend.global.exception.BusinessException;
import com.siot.backend.global.exception.ErrorCode;
import com.siot.backend.global.mail.EmailSender;
import com.siot.backend.global.mail.MailTemplates;
import com.siot.backend.member.domain.AppUser;
import com.siot.backend.member.dto.EmailVerificationResponse;
import com.siot.backend.member.dto.MemberResponse;
import com.siot.backend.member.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;

/**
 * NTF-09: 알림 이메일 선택 등록. 인증 코드를 메일로 보내고, 코드를 확인한 뒤에만 계정에 저장한다.
 * <p>
 * 인증 대기 정보는 Redis 에 10분 동안 보관한다. (DB에는 인증이 끝난 이메일만 저장)
 */
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    static final Duration CODE_TTL = Duration.ofMinutes(10);
    static final int MAX_ATTEMPTS = 5;
    /** Gmail 발송량 제한(하루 약 500통) 보호를 위해 회원당 요청 횟수를 제한한다 */
    static final Duration RESEND_INTERVAL = Duration.ofMinutes(1);
    static final int DAILY_LIMIT = 10;

    private static final String PENDING_KEY = "siot:email-verification:";

    private final AppUserRepository appUserRepository;
    private final StringRedisTemplate redisTemplate;
    private final RequestRateLimiter rateLimiter;
    private final EmailSender emailSender;
    private final MailTemplates mailTemplates;
    private final Clock clock;

    /** 인증 코드 발송. 같은 회원이 다시 요청하면 이전 코드는 무효가 된다. */
    public EmailVerificationResponse requestVerification(Long memberId, String rawEmail) {
        AppUser user = findMember(memberId);
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);

        switch (rateLimiter.tryAcquire("email-verification:" + memberId, RESEND_INTERVAL, DAILY_LIMIT, Duration.ofDays(1))) {
            case TOO_FREQUENT -> throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_TOO_FREQUENT);
            case LIMIT_EXCEEDED -> throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_DAILY_LIMIT);
            case ALLOWED -> { }
        }

        String code = TokenUtils.numericCode(6);
        String key = PENDING_KEY + memberId;
        redisTemplate.delete(key);
        redisTemplate.opsForHash().putAll(key, Map.of(
                "email", email,
                "codeHash", TokenUtils.sha256(code),
                "attempts", "0"));
        redisTemplate.expire(key, CODE_TTL);

        emailSender.send(mailTemplates.emailVerification(email, user.getLanguage(), code, CODE_TTL.toMinutes()));
        return new EmailVerificationResponse(email, CODE_TTL.toSeconds(), RESEND_INTERVAL.toSeconds());
    }

    /** 코드가 맞으면 인증된 알림 이메일로 저장한다. 5회 틀리면 코드를 폐기한다. */
    @Transactional
    public MemberResponse verify(Long memberId, String code) {
        String key = PENDING_KEY + memberId;
        Map<Object, Object> pending = redisTemplate.opsForHash().entries(key);
        if (pending.isEmpty()) {
            throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_EXPIRED);
        }

        if (!TokenUtils.constantTimeEquals(TokenUtils.sha256(code.trim()), (String) pending.get("codeHash"))) {
            Long attempts = redisTemplate.opsForHash().increment(key, "attempts", 1);
            if (attempts >= MAX_ATTEMPTS) {
                redisTemplate.delete(key);
                throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_EXPIRED);
            }
            throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_CODE_INVALID);
        }

        redisTemplate.delete(key);
        AppUser user = findMember(memberId);
        user.registerVerifiedEmail((String) pending.get("email"), LocalDateTime.now(clock));
        return MemberResponse.from(user);
    }

    /** 알림 이메일 등록 해제 (알림 메일·비밀번호 재설정을 더 이상 받지 않음) */
    @Transactional
    public MemberResponse remove(Long memberId) {
        redisTemplate.delete(PENDING_KEY + memberId);
        AppUser user = findMember(memberId);
        user.removeEmail();
        return MemberResponse.from(user);
    }

    private AppUser findMember(Long memberId) {
        return appUserRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }
}
