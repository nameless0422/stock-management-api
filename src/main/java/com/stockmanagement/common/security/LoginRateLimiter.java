package com.stockmanagement.common.security;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 로그인 브루트포스 방어용 Rate Limiter.
 *
 * <p>Redis {@code RAtomicLong}으로 로그인 시도 횟수를 추적한다.
 * 키 형식: {@code rate-limit:login:{username}}
 * 15분 윈도우 내 5회 초과 시 {@link ErrorCode#TOO_MANY_LOGIN_ATTEMPTS} 예외.
 */
@Component
@RequiredArgsConstructor
public class LoginRateLimiter {

    private static final String KEY_PREFIX = "rate-limit:login:";
    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_MINUTES = 15;

    private final RedissonClient redissonClient;

    /**
     * 시도 횟수를 1 증가시키고, 한도 초과 시 예외를 던진다.
     * 첫 번째 시도일 때 TTL 15분을 설정한다.
     */
    public void checkAndIncrement(String username) {
        RAtomicLong counter = redissonClient.getAtomicLong(KEY_PREFIX + username);
        long count = counter.incrementAndGet();
        if (count == 1) {
            counter.expire(WINDOW_MINUTES, TimeUnit.MINUTES);
        }
        if (count > MAX_ATTEMPTS) {
            throw new BusinessException(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS);
        }
    }

    /** 로그인 성공 시 카운터를 초기화한다. */
    public void reset(String username) {
        redissonClient.getAtomicLong(KEY_PREFIX + username).delete();
    }
}
