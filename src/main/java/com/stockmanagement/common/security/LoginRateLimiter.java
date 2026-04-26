package com.stockmanagement.common.security;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.LongCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;

/**
 * 로그인 브루트포스 방어용 Rate Limiter.
 *
 * <p>Redis Lua 스크립트로 INCR + EXPIRE를 원자적으로 실행한다.
 * INCR만 성공하고 앱이 크래시해도 TTL이 설정되어 키가 영구 잔존하는 버그를 방지한다.
 * 키 형식: {@code rate-limit:login:{username}:{ip}}
 * username+IP 복합 키로 공격자가 타인의 계정을 잠그는 Account Lockout DoS를 방지한다.
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

    @Value("${rate-limit.trust-proxy:true}")
    private boolean trustProxy;

    @Value("${rate-limit.trusted-proxy-count:1}")
    private int trustedProxyCount;

    /**
     * 시도 횟수를 1 증가시키고, 한도 초과 시 예외를 던진다.
     * username + IP 복합 키를 사용하여 공격자가 타인 계정을 잠그지 못하게 한다.
     */
    public void checkAndIncrement(String username) {
        String key = KEY_PREFIX + username + ":" + extractClientIp();
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

    /** 로그인 성공 시 해당 username+IP 버킷 카운터를 초기화한다. */
    public void reset(String username) {
        String key = KEY_PREFIX + username + ":" + extractClientIp();
        redissonClient.getBucket(key).delete();
    }

    /**
     * 현재 요청의 클라이언트 IP를 추출한다.
     * RateLimitAspect.resolveClientIp()와 동일한 trust-proxy 전략을 사용한다.
     */
    private String extractClientIp() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return "unknown";
            HttpServletRequest request = attrs.getRequest();

            if (trustProxy) {
                String realIp = request.getHeader("X-Real-IP");
                if (realIp != null && !realIp.isBlank()) return realIp.trim();

                String forwarded = request.getHeader("X-Forwarded-For");
                if (forwarded != null && !forwarded.isBlank()) {
                    String[] ips = forwarded.split(",");
                    int idx = Math.max(0, ips.length - 1 - trustedProxyCount);
                    return ips[idx].trim();
                }
            }
            return request.getRemoteAddr();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
