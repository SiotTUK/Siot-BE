package com.siot.backend.global.common;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * 이메일 인증 코드(6자리) 보관·확인. Redis 에 코드 해시만 저장한다 (원문 저장 안 함).
 * 키: {@code siot:{namespace}:{subject}} — 예: 가입 전 인증은 이메일, 가입 후 인증은 회원 ID.
 */
@Component
@RequiredArgsConstructor
public class VerificationCodeStore {

    public enum Result { VERIFIED, INVALID, EXPIRED }

    private final StringRedisTemplate redisTemplate;

    /** 새 코드를 발급한다. 같은 대상의 이전 코드는 무효가 된다. */
    public String issue(String namespace, String subject, String email, Duration ttl) {
        String code = TokenUtils.numericCode(6);
        String key = key(namespace, subject);
        redisTemplate.delete(key);
        redisTemplate.opsForHash().putAll(key, Map.of(
                "email", email,
                "codeHash", TokenUtils.sha256(code),
                "attempts", "0"));
        redisTemplate.expire(key, ttl);
        return code;
    }

    /**
     * 코드를 확인한다. 맞으면 코드를 지우고 VERIFIED.
     * 틀린 횟수가 maxAttempts 에 이르거나, 코드가 없거나, 발급 때와 이메일이 다르면 EXPIRED.
     */
    public Result verify(String namespace, String subject, String email, String code, int maxAttempts) {
        String key = key(namespace, subject);
        Map<Object, Object> pending = redisTemplate.opsForHash().entries(key);
        if (pending.isEmpty() || !email.equals(pending.get("email"))) {
            return Result.EXPIRED;
        }
        if (!TokenUtils.constantTimeEquals(TokenUtils.sha256(code.trim()), (String) pending.get("codeHash"))) {
            Long attempts = redisTemplate.opsForHash().increment(key, "attempts", 1);
            if (attempts >= maxAttempts) {
                redisTemplate.delete(key);
                return Result.EXPIRED;
            }
            return Result.INVALID;
        }
        redisTemplate.delete(key);
        return Result.VERIFIED;
    }

    private String key(String namespace, String subject) {
        return "siot:" + namespace + ":" + subject;
    }
}
