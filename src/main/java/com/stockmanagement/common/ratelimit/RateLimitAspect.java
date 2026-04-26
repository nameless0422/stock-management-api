package com.stockmanagement.common.ratelimit;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.LongCodec;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;

/**
 * {@link RateLimit} 어노테이션을 처리하는 AOP 어스펙트.
 *
 * <p>Redis {@code RAtomicLong}으로 시간 윈도우 내 요청 횟수를 추적한다.
 * 한도 초과 시 {@link ErrorCode#TOO_MANY_REQUESTS} 예외를 던진다.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private static final String KEY_PREFIX = "rate-limit:api:";

    // INCR과 EXPIRE를 원자적으로 실행하는 Lua 스크립트.
    // INCR만 성공하고 EXPIRE 전에 앱이 크래시해도 키가 TTL 없이 영구 잔존하는 문제를 방지한다.
    private static final String INCR_AND_EXPIRE_SCRIPT =
            "local count = redis.call('INCR', KEYS[1]) " +
            "if count == 1 then " +
            "    redis.call('EXPIRE', KEYS[1], ARGV[1]) " +
            "end " +
            "return count";

    private final RedissonClient redissonClient;

    /** false이면 X-Real-IP / X-Forwarded-For 헤더를 무시하고 RemoteAddr만 사용 (직접 노출 환경용) */
    @Value("${rate-limit.trust-proxy:true}")
    private boolean trustProxy;

    /** 신뢰하는 프록시 수 — X-Forwarded-For 끝에서 이 값만큼 건너뛰어 실제 클라이언트 IP 추출 */
    @Value("${rate-limit.trusted-proxy-count:1}")
    private int trustedProxyCount;

    @Around("@annotation(com.stockmanagement.common.ratelimit.RateLimit)")
    public Object rateLimit(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        RateLimit rateLimit = signature.getMethod().getAnnotation(RateLimit.class);

        String identifier = resolveIdentifier(rateLimit.keyType());
        String endpointKey = signature.getDeclaringType().getSimpleName() + ":" + signature.getMethod().getName();
        String redisKey = KEY_PREFIX + identifier + ":" + endpointKey;

        // INCR + 조건부 EXPIRE를 Lua 스크립트로 원자 실행
        long count = redissonClient.getScript(LongCodec.INSTANCE).eval(
                RScript.Mode.READ_WRITE,
                INCR_AND_EXPIRE_SCRIPT,
                RScript.ReturnType.INTEGER,
                Collections.singletonList(redisKey),
                (long) rateLimit.windowSeconds()
        );

        if (count > rateLimit.limit()) {
            log.warn("Rate limit 초과: key={}, count={}, limit={}", redisKey, count, rateLimit.limit());
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }

        return joinPoint.proceed();
    }

    /**
     * keyType에 따라 요청 식별자를 반환한다.
     * USER: 인증된 사용자명, 미인증 시 IP로 폴백
     * IP: 클라이언트 IP (X-Forwarded-For 우선)
     */
    private String resolveIdentifier(RateLimit.KeyType keyType) {
        if (keyType == RateLimit.KeyType.USER) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                return "user:" + auth.getName();
            }
        }
        return "ip:" + resolveClientIp();
    }

    /**
     * 클라이언트 IP 추출.
     * trust-proxy=true (기본): X-Real-IP → X-Forwarded-For → RemoteAddr 순으로 사용.
     * trust-proxy=false: 프록시 없이 직접 노출된 환경 — RemoteAddr만 사용 (헤더 위조 방지).
     *
     * <p>X-Forwarded-For 처리: "client, proxy1, proxy2"에서 trusted-proxy-count=1이면
     * 끝에서 1개(proxy2 제거)를 건너뛰어 proxy1 위치를 실제 클라이언트 IP로 간주.
     * 공격자가 헤더를 "attacker, real_client, proxy"로 조작해도 proxy를 제외한 real_client를 사용.
     */
    private String resolveClientIp() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = attrs.getRequest();

        if (trustProxy) {
            // X-Real-IP: nginx 등 리버스 프록시가 설정한 단일 IP (스푸핑 불가)
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return realIp.trim();
            }

            // X-Forwarded-For: 끝에서 trustedProxyCount번째 IP를 클라이언트 IP로 사용
            // trustedProxyCount=1: "client, proxy1" → index 0 = client IP
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String[] ips = forwarded.split(",");
                int idx = Math.max(0, ips.length - 1 - trustedProxyCount);
                return ips[idx].trim();
            }
        }

        return request.getRemoteAddr();
    }
}
