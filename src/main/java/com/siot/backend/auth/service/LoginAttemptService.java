package com.siot.backend.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;

/**
 * M-08: 5회 연속 로그인 실패 시 5분간 로그인 차단 (Redis TTL).
 * 존재하지 않는 ID도 같은 방식으로 세어 ID 존재 여부가 드러나지 않게 한다.
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    static final int MAX_FAILURES = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(5);
    /** 연속 실패 횟수 보관 기간 (이 기간 동안 성공이 없으면 계속 누적) */
    static final Duration FAILURE_WINDOW = Duration.ofDays(1);

    private static final String FAILURE_KEY = "siot:login:failures:";
    private static final String LOCK_KEY = "siot:login:lock:";

    private final StringRedisTemplate redisTemplate;

    public boolean isLocked(String loginId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(LOCK_KEY + normalize(loginId)));
    }

    public void recordFailure(String loginId) {
        String key = FAILURE_KEY + normalize(loginId);
        Long failures = redisTemplate.opsForValue().increment(key);
        if (failures != null && failures == 1) {
            redisTemplate.expire(key, FAILURE_WINDOW);
        }
        if (failures != null && failures >= MAX_FAILURES) {
            redisTemplate.opsForValue().set(LOCK_KEY + normalize(loginId), "1", LOCK_DURATION);
            redisTemplate.delete(key);
        }
    }

    public void recordSuccess(String loginId) {
        redisTemplate.delete(FAILURE_KEY + normalize(loginId));
    }

    /** 비밀번호를 재설정하면 실패 기록과 잠금을 모두 풀어 바로 로그인할 수 있게 한다 */
    public void clear(String loginId) {
        redisTemplate.delete(java.util.List.of(FAILURE_KEY + normalize(loginId), LOCK_KEY + normalize(loginId)));
    }

    private String normalize(String loginId) {
        return loginId == null ? "" : loginId.trim().toLowerCase(Locale.ROOT);
    }
}
