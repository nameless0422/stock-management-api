package com.stockmanagement.common.security;

import com.stockmanagement.common.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * JWT 로그아웃 블랙리스트.
 *
 * <p>로그아웃 시 토큰의 {@code jti}를 키로 Redis에 저장한다.
 * TTL은 토큰 만료까지 남은 시간으로 설정해 자동 만료.
 * 키 형식: {@code blacklist:jwt:{jti}}
 */
@Component
@RequiredArgsConstructor
public class JwtBlacklist {

    private static final String KEY_PREFIX = "blacklist:jwt:";

    private final RedissonClient redissonClient;
    private final JwtTokenProvider jwtTokenProvider;

    /** 토큰을 블랙리스트에 등록한다. */
    public void revoke(String token) {
        String jti = jwtTokenProvider.getJti(token);
        long remainingSeconds = jwtTokenProvider.getRemainingSeconds(token);
        if (remainingSeconds > 0) {
            redissonClient.<String>getBucket(KEY_PREFIX + jti)
                    .set("revoked", Duration.ofSeconds(remainingSeconds));
        }
    }

    /** 토큰이 블랙리스트에 등록되어 있으면 true를 반환한다. */
    public boolean isRevoked(String token) {
        String jti = jwtTokenProvider.getJti(token);
        return redissonClient.getBucket(KEY_PREFIX + jti).isExists();
    }
}
