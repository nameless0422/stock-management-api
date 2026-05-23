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
 * 이메일 인증 토큰 저장소 (Redis 기반).
 *
 * <p>키 구조: {@code email-verify:token:{uuid}} → username (TTL: 24시간)
 * <p>일회용 — {@link #consume(String)} 호출 시 즉시 삭제된다.
 */
@Component
@RequiredArgsConstructor
public class EmailVerificationTokenStore {

    private static final String TOKEN_PREFIX = "email-verify:token:";
    private static final long TTL_HOURS = 24;

    private final RedissonClient redissonClient;

    /** 이메일 인증 토큰 발급. UUID 생성 후 Redis에 저장하고 토큰 문자열을 반환한다. */
    public String issue(String username) {
        String token = UUID.randomUUID().toString();
        redissonClient.<String>getBucket(TOKEN_PREFIX + token)
                .set(username, TTL_HOURS, TimeUnit.HOURS);
        return token;
    }

    /**
     * 토큰 소비(일회용). 토큰을 삭제하고 저장된 username을 반환한다.
     *
     * @throws BusinessException 토큰이 존재하지 않거나 만료된 경우
     */
    public String consume(String token) {
        RBucket<String> bucket = redissonClient.getBucket(TOKEN_PREFIX + token);
        String username = bucket.getAndDelete();
        if (username == null) {
            throw new BusinessException(ErrorCode.INVALID_VERIFICATION_TOKEN);
        }
        return username;
    }
}
