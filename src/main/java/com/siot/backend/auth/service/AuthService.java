package com.siot.backend.auth.service;

import com.siot.backend.auth.domain.CredentialPolicy;
import com.siot.backend.auth.dto.LoginRequest;
import com.siot.backend.auth.dto.SignupRequest;
import com.siot.backend.auth.security.MemberPrincipal;
import com.siot.backend.auth.security.SessionAuthenticator;
import com.siot.backend.global.exception.BusinessException;
import com.siot.backend.global.exception.ErrorCode;
import com.siot.backend.global.common.SupportedLanguages;
import com.siot.backend.member.config.AdminProperties;
import com.siot.backend.member.domain.AppUser;
import com.siot.backend.member.domain.Role;
import com.siot.backend.member.dto.MemberResponse;
import com.siot.backend.member.repository.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionAuthenticator sessionAuthenticator;
    private final LoginAttemptService loginAttemptService;
    private final SignupVerificationService signupVerificationService;
    private final Clock clock;
    private final AdminProperties adminProperties;

    /** M-02: 이메일 사용 가능 여부 (형식이 틀리면 EMAIL_FORMAT) */
    @Transactional(readOnly = true)
    public boolean isEmailAvailable(String email) {
        String normalized = CredentialPolicy.normalizeEmail(email);
        CredentialPolicy.validateEmail(normalized);
        return !appUserRepository.existsByEmail(normalized);
    }

    /** M-01 ~ M-03: 인증 코드를 확인한 이메일로 가입하고 바로 로그인 상태로 만든다. */
    @Transactional
    public MemberResponse signup(SignupRequest request,
                                 HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String email = CredentialPolicy.normalizeEmail(request.email());
        CredentialPolicy.validateEmail(email);
        CredentialPolicy.validatePassword(request.password(), email);
        if (!request.password().equals(request.passwordConfirm())) {
            throw new BusinessException(ErrorCode.PASSWORD_CONFIRM_MISMATCH);
        }
        if (appUserRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        if (!signupVerificationService.isVerified(email)) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        AppUser user;
        try {
            // M-02: 동시 가입은 DB UNIQUE 제약으로 한 번 더 막는다
            AppUser newUser = AppUser.create(email, passwordEncoder.encode(request.password()));
            newUser.markEmailVerified(OffsetDateTime.now(clock));
            newUser.changeLanguage(SupportedLanguages.orDefault(request.language()));
            // ADM-01: SIOT_ADMIN_EMAILS 에 있는 이메일은 가입과 동시에 관리자
            if (adminProperties.isAdminEmail(email)) {
                newUser.changeRole(Role.ADMIN);
            }
            user = appUserRepository.saveAndFlush(newUser);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        signupVerificationService.consume(email);

        sessionAuthenticator.login(MemberPrincipal.from(user), httpRequest, httpResponse);
        return MemberResponse.from(user);
    }

    /** M-08: 로그인. 실패 사유는 구분하지 않고, 5회 연속 실패 시 5분간 차단한다. */
    @Transactional(readOnly = true)
    public MemberResponse login(LoginRequest request,
                                HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String email = CredentialPolicy.normalizeEmail(request.email());
        if (loginAttemptService.isLocked(email)) {
            throw new BusinessException(ErrorCode.LOGIN_LOCKED);
        }

        MemberPrincipal principal;
        try {
            principal = sessionAuthenticator.login(email, request.password(), httpRequest, httpResponse);
        } catch (AuthenticationException e) {
            // 화면 "비밀번호가 틀렸어요 (2/5)". 가입하지 않은 이메일도 같은 방식으로 세므로 가입 여부는 드러나지 않는다
            int failures = loginAttemptService.recordFailure(email);
            int max = loginAttemptService.maxFailures();
            throw new BusinessException(ErrorCode.LOGIN_FAILED, Map.of(
                    "failedAttempts", failures,
                    "maxAttempts", max,
                    "remainingAttempts", max - failures));
        }
        loginAttemptService.recordSuccess(email);

        return appUserRepository.findById(principal.getId())
                .map(MemberResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));
    }

    /** M-09: 로그아웃 */
    public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        sessionAuthenticator.logout(httpRequest, httpResponse);
    }
}
