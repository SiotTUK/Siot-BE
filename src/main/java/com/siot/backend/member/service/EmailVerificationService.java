package com.siot.backend.member.service;

import com.siot.backend.global.common.RequestRateLimiter;
import com.siot.backend.global.common.VerificationCodeStore;
import com.siot.backend.global.exception.BusinessException;
import com.siot.backend.global.exception.ErrorCode;
import com.siot.backend.global.mail.EmailSender;
import com.siot.backend.global.mail.MailTemplates;
import com.siot.backend.member.domain.AppUser;
import com.siot.backend.member.dto.EmailVerificationResponse;
import com.siot.backend.member.dto.MemberResponse;
import com.siot.backend.member.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * NTF-09: 가입한 뒤의 이메일 인증. 새 회원은 가입 때 인증을 마치므로(SignupVerificationService),
 * 인증 기능이 생기기 전에 가입한 미인증 회원이 사용한다.
 * 인증된 회원만 비밀번호 재설정(M-11)·이메일 알림(NTF-05)을 받는다.
 */
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    static final Duration CODE_TTL = Duration.ofMinutes(10);
    static final int MAX_ATTEMPTS = 5;
    /** Gmail 발송량 제한(하루 약 500통) 보호를 위해 회원당 요청 횟수를 제한한다 */
    static final Duration RESEND_INTERVAL = Duration.ofMinutes(1);
    static final int DAILY_LIMIT = 10;

    private static final String NAMESPACE = "email-verification";

    private final AppUserRepository appUserRepository;
    private final VerificationCodeStore codeStore;
    private final RequestRateLimiter rateLimiter;
    private final EmailSender emailSender;
    private final MailTemplates mailTemplates;
    private final Clock clock;

    /** 가입 이메일로 인증 코드 발송. 다시 요청하면 이전 코드는 무효가 된다. */
    public EmailVerificationResponse requestVerification(Long memberId) {
        AppUser user = findMember(memberId);
        if (user.hasVerifiedEmail()) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_VERIFIED);
        }

        switch (rateLimiter.tryAcquire(NAMESPACE + ":" + memberId, RESEND_INTERVAL, DAILY_LIMIT, Duration.ofDays(1))) {
            case TOO_FREQUENT -> throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_TOO_FREQUENT);
            case LIMIT_EXCEEDED -> throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_DAILY_LIMIT);
            case ALLOWED -> { }
        }

        String code = codeStore.issue(NAMESPACE, String.valueOf(memberId), user.getEmail(), CODE_TTL);
        emailSender.send(mailTemplates.emailVerification(user.getEmail(), user.getLanguage(), code, CODE_TTL.toMinutes()));
        return new EmailVerificationResponse(user.getEmail(), CODE_TTL.toSeconds(), RESEND_INTERVAL.toSeconds());
    }

    /** 코드가 맞으면 인증 완료로 기록한다. 5회 틀리면 코드를 폐기한다. */
    @Transactional
    public MemberResponse verify(Long memberId, String code) {
        AppUser user = findMember(memberId);
        switch (codeStore.verify(NAMESPACE, String.valueOf(memberId), user.getEmail(), code, MAX_ATTEMPTS)) {
            case INVALID -> throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_CODE_INVALID);
            case EXPIRED -> throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_EXPIRED);
            case VERIFIED -> { }
        }
        user.markEmailVerified(LocalDateTime.now(clock));
        return MemberResponse.from(user);
    }

    private AppUser findMember(Long memberId) {
        return appUserRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }
}
