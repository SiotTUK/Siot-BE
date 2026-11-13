package com.siot.backend.global.common;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis 기반 요청 횟수 제한: "간격당 1회" + "기간당 최대 N회".
 * 예: 비밀번호 재설정 요청 1분에 1회, 하루 5회 (M-11)
 */
@Component
@RequiredArgsConstructor
public class RequestRateLimiter {

    public enum Result { ALLOWED, TOO_FREQUENT, LIMIT_EXCEEDED }

    private static final String PREFIX = "siot:rate:";

    private final StringRedisTemplate redisTemplate;

    /**
     * 허용되면 사용 횟수를 기록한다.
     *
     * @param key       제한 대상 (예: "password-reset:user01")
     * @param interval  연속 요청 최소 간격
     * @param maxCount  window 기간 동안 최대 요청 수
     * @param window    횟수를 세는 기간 (첫 요청부터 시작)
     */
    public Result tryAcquire(String key, Duration interval, int maxCount, Duration window) {
        String intervalKey = PREFIX + key + ":interval";
        String countKey = PREFIX + key + ":count";

        String count = redisTemplate.opsForValue().get(countKey);
        if (count != null && Integer.parseInt(count) >= maxCount) {
            return Result.LIMIT_EXCEEDED;
        }
        Boolean first = redisTemplate.opsForValue().setIfAbsent(intervalKey, "1", interval);
        if (!Boolean.TRUE.equals(first)) {
            return Result.TOO_FREQUENT;
        }
        Long current = redisTemplate.opsForValue().increment(countKey);
        if (current != null && current == 1) {
            redisTemplate.expire(countKey, window);
        }
        return Result.ALLOWED;
    }
}
