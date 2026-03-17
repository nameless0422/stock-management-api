package com.stockmanagement.common.security;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Refresh Token 저장소 (Redis 기반).
 *
 * <p>키: {@code refresh:token:{uuid}}, 값: username, TTL: 30일.
 * 토큰 사용 시 즉시 삭제(rotation)하여 재사용을 방지한다.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenStore {

    private static final String PREFIX = "refresh:token:";
    private static final long TTL_DAYS = 30;

    private final RedissonClient redissonClient;

    /**
     * Refresh Token 발급. UUID 생성 후 Redis에 저장하고 토큰 문자열을 반환한다.
     */
    public String issue(String username) {
        String token = UUID.randomUUID().toString();
        RBucket<String> bucket = redissonClient.getBucket(PREFIX + token);
        bucket.set(username, TTL_DAYS, TimeUnit.DAYS);
        return token;
    }

    /**
     * Refresh Token 소비(rotation). 토큰을 삭제하고 저장된 username을 반환한다.
     *
     * @throws BusinessException 토큰이 존재하지 않거나 만료된 경우
     */
    public String consume(String token) {
        RBucket<String> bucket = redissonClient.getBucket(PREFIX + token);
        String username = bucket.getAndDelete();
        if (username == null) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        return username;
    }

    /** Refresh Token 즉시 무효화 (로그아웃 시 호출). */
    public void revoke(String token) {
        redissonClient.getBucket(PREFIX + token).delete();
    }
}
