package com.siot.backend.global.common;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 이메일 인증 코드(6자리) 보관·확인. Redis 에 코드 해시만 저장한다 (원문 저장 안 함).
 * 키: {@code siot:{namespace}:{subject}} — 예: 가입 전 인증은 이메일, 가입 후 인증은 회원 ID.
 */
@Component
@RequiredArgsConstructor
public class VerificationCodeStore {

    public enum Result { VERIFIED, INVALID, EXPIRED }

    private final StringRedisTemplate redisTemplate;

    /**
     * 삭제·저장·만료 설정을 한 번에 실행한다. 따로 실행하면 그 사이에 서버가 멈출 때 만료 시간 없는 키가 남는다.
     * KEYS[1] = 코드 키, ARGV = email, 코드 해시, 만료(ms)
     */
    private static final RedisScript<Long> ISSUE_SCRIPT = new DefaultRedisScript<>("""
            redis.call('DEL', KEYS[1])
            redis.call('HSET', KEYS[1], 'email', ARGV[1], 'codeHash', ARGV[2], 'attempts', '0')
            redis.call('PEXPIRE', KEYS[1], ARGV[3])
            return 1
            """, Long.class);

    /** 새 코드를 발급한다. 같은 대상의 이전 코드는 무효가 된다. */
    public String issue(String namespace, String subject, String email, Duration ttl) {
        String code = TokenUtils.numericCode(6);
        redisTemplate.execute(ISSUE_SCRIPT, List.of(key(namespace, subject)),
                email, TokenUtils.sha256(code), String.valueOf(ttl.toMillis()));
        return code;
    }

    /**
     * 확인·틀린 횟수 증가·삭제를 한 번에 처리한다 (Redis 는 스크립트를 원자적으로 실행).
     * 따로 나눠 실행하면 동시에 보낸 요청들이 삭제 전의 코드와 비교되어 5회 제한을 넘겨 맞혀 볼 수 있고,
     * 삭제된 뒤의 HINCRBY 가 만료 시간 없는 키를 새로 만든다.
     * KEYS[1] = 코드 키, ARGV = email, 입력 코드 해시, maxAttempts
     */
    private static final RedisScript<String> VERIFY_SCRIPT = new DefaultRedisScript<>("""
            local h = redis.call('HMGET', KEYS[1], 'email', 'codeHash', 'attempts')
            if not h[1] or h[1] ~= ARGV[1] then
                return 'EXPIRED'
            end
            local max = tonumber(ARGV[3])
            if (tonumber(h[3]) or 0) >= max then
                redis.call('DEL', KEYS[1])
                return 'EXPIRED'
            end
            if h[2] == ARGV[2] then
                redis.call('DEL', KEYS[1])
                return 'VERIFIED'
            end
            if redis.call('HINCRBY', KEYS[1], 'attempts', 1) >= max then
                redis.call('DEL', KEYS[1])
                return 'EXPIRED'
            end
            return 'INVALID'
            """, String.class);

    /**
     * 코드를 확인한다. 맞으면 코드를 지우고 VERIFIED.
     * 틀린 횟수가 maxAttempts 에 이르거나, 코드가 없거나, 발급 때와 이메일이 다르면 EXPIRED.
     */
    public Result verify(String namespace, String subject, String email, String code, int maxAttempts) {
        String result = redisTemplate.execute(VERIFY_SCRIPT, List.of(key(namespace, subject)),
                email, TokenUtils.sha256(code.trim()), String.valueOf(maxAttempts));
        return result == null ? Result.EXPIRED : Result.valueOf(result);
    }

    private String key(String namespace, String subject) {
        return "siot:" + namespace + ":" + subject;
    }
}
