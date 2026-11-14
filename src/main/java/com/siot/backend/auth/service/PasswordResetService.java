package com.siot.backend.auth.service;

import com.siot.backend.auth.domain.CredentialPolicy;
import com.siot.backend.global.common.RequestRateLimiter;
import com.siot.backend.global.common.TokenUtils;
import com.siot.backend.global.config.FrontendProperties;
import com.siot.backend.global.exception.BusinessException;
import com.siot.backend.global.exception.ErrorCode;
import com.siot.backend.global.mail.EmailSender;
import com.siot.backend.global.mail.MailTemplates;
import com.siot.backend.member.domain.AppUser;
import com.siot.backend.member.event.MemberPasswordResetEvent;
import com.siot.backend.member.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;

/**
 * M-11: 비밀번호 재설정.
 * <ol>
 *   <li>이메일(아이디) 입력 → 가입하고 인증(NTF-09)까지 마친 이메일이면 재설정 링크 발송</li>
 *   <li>가입·인증 여부와 관계없이 같은 응답</li>
 *   <li>링크는 1회용, 30분 후 만료</li>
 *   <li>재설정이 끝나면 그 회원의 모든 세션 삭제</li>
 *   <li>요청 횟수: 이메일 기준 1분에 1회, 하루 5회</li>
 * </ol>
 * 토큰 원문은 메일 링크에만 들어가고, Redis 에는 SHA-256 해시만 저장한다.
 */
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    static final Duration TOKEN_TTL = Duration.ofMinutes(30);
    static final Duration REQUEST_INTERVAL = Duration.ofMinutes(1);
    static final int DAILY_LIMIT = 5;
    /** 프론트엔드 재설정 화면 경로 (?token=...) */
    static final String RESET_PAGE_PATH = "/password-reset";

    private static final String TOKEN_KEY = "siot:password-reset:token:";
    private static final String USER_KEY = "siot:password-reset:user:";

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;
    private final RequestRateLimiter rateLimiter;
    private final EmailSender emailSender;
    private final MailTemplates mailTemplates;
    private final FrontendProperties frontendProperties;
    private final LoginAttemptService loginAttemptService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public void requestReset(String rawEmail) {
        String email = CredentialPolicy.normalizeEmail(rawEmail);

        switch (rateLimiter.tryAcquire("password-reset:" + email, REQUEST_INTERVAL, DAILY_LIMIT, Duration.ofDays(1))) {
            case TOO_FREQUENT -> throw new BusinessException(ErrorCode.PASSWORD_RESET_TOO_FREQUENT);
            case LIMIT_EXCEEDED -> throw new BusinessException(ErrorCode.PASSWORD_RESET_DAILY_LIMIT);
            case ALLOWED -> { }
        }

        appUserRepository.findByEmail(email)
                .filter(AppUser::hasVerifiedEmail)
                .ifPresent(this::issueToken);
    }

    /** 재설정 화면 진입 시 링크가 아직 유효한지 확인 */
    public void validateToken(String token) {
        if (findUserId(token) == null) {
            throw new BusinessException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID);
        }
    }

    @Transactional
    public void confirmReset(String token, String newPassword, String passwordConfirm) {
        String userId = findUserId(token);
        if (userId == null) {
            throw new BusinessException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID);
        }
        AppUser user = appUserRepository.findById(Long.valueOf(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID));

        // 비밀번호 규칙을 먼저 검사해서, 입력 실수로 링크가 소모되지 않게 한다
        CredentialPolicy.validatePassword(newPassword, user.getEmail());
        if (!newPassword.equals(passwordConfirm)) {
            throw new BusinessException(ErrorCode.PASSWORD_CONFIRM_MISMATCH);
        }

        // 1회용: 원자적으로 꺼내면서 삭제 (동시에 두 번 써도 한 번만 성공)
        String tokenHash = TokenUtils.sha256(token);
        if (redisTemplate.opsForValue().getAndDelete(TOKEN_KEY + tokenHash) == null) {
            throw new BusinessException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID);
        }
        redisTemplate.delete(USER_KEY + userId);

        user.changePasswordHash(passwordEncoder.encode(newPassword));
        loginAttemptService.clear(user.getEmail());
        // 모든 세션 삭제는 커밋 후 (MemberSessionService)
        eventPublisher.publishEvent(new MemberPasswordResetEvent(user.getId(), user.getEmail()));
    }

    private void issueToken(AppUser user) {
        // 회원당 유효한 링크는 하나만 유지한다 (이전 링크 무효화)
        String previousHash = redisTemplate.opsForValue().get(USER_KEY + user.getId());
        if (previousHash != null) {
            redisTemplate.delete(TOKEN_KEY + previousHash);
        }

        String token = TokenUtils.urlSafeToken();
        String tokenHash = TokenUtils.sha256(token);
        redisTemplate.opsForValue().set(TOKEN_KEY + tokenHash, String.valueOf(user.getId()), TOKEN_TTL);
        redisTemplate.opsForValue().set(USER_KEY + user.getId(), tokenHash, TOKEN_TTL);

        String link = UriComponentsBuilder.fromUriString(frontendProperties.url(RESET_PAGE_PATH))
                .queryParam("token", token)
                .toUriString();
        emailSender.send(mailTemplates.passwordReset(
                user.getEmail(), user.getLanguage(), link, TOKEN_TTL.toMinutes()));
    }

    private String findUserId(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return redisTemplate.opsForValue().get(TOKEN_KEY + TokenUtils.sha256(token));
    }
}
