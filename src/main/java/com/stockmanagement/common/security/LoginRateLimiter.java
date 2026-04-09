package com.stockmanagement.common.security;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.LongCodec;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * 로그인 브루트포스 방어용 Rate Limiter.
 *
 * <p>Redis Lua 스크립트로 INCR + EXPIRE를 원자적으로 실행한다.
 * INCR만 성공하고 앱이 크래시해도 TTL이 설정되어 키가 영구 잔존하는 버그를 방지한다.
 * 키 형식: {@code rate-limit:login:{username}}
 * 15분 윈도우 내 5회 초과 시 {@link ErrorCode#TOO_MANY_LOGIN_ATTEMPTS} 예외.
 */
@Component
@RequiredArgsConstructor
public class LoginRateLimiter {

    private static final String KEY_PREFIX = "rate-limit:login:";
    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_SECONDS = 15 * 60;

    // INCR과 EXPIRE를 원자적으로 실행하는 Lua 스크립트.
    // count == 1일 때만 EXPIRE를 설정하여 윈도우 시작 시각을 고정한다.
    private static final String INCR_AND_EXPIRE_SCRIPT =
            "local count = redis.call('INCR', KEYS[1]) " +
            "if count == 1 then " +
            "    redis.call('EXPIRE', KEYS[1], ARGV[1]) " +
            "end " +
            "return count";

    private final RedissonClient redissonClient;

    /**
     * 시도 횟수를 1 증가시키고, 한도 초과 시 예외를 던진다.
     * 첫 번째 시도일 때 TTL을 Lua 스크립트 내에서 원자적으로 설정한다.
     */
    public void checkAndIncrement(String username) {
        String key = KEY_PREFIX + username;
        long count = redissonClient.getScript(LongCodec.INSTANCE).eval(
                RScript.Mode.READ_WRITE,
                INCR_AND_EXPIRE_SCRIPT,
                RScript.ReturnType.INTEGER,
                Collections.singletonList(key),
                WINDOW_SECONDS
        );
        if (count > MAX_ATTEMPTS) {
            throw new BusinessException(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS);
        }
    }

    /** 로그인 성공 시 카운터를 초기화한다. */
    public void reset(String username) {
        redissonClient.getBucket(KEY_PREFIX + username).delete();
    }
}
