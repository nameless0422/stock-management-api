package com.stockmanagement.common.ratelimit;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.TimeUnit;

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

    private final RedissonClient redissonClient;

    @Around("@annotation(com.stockmanagement.common.ratelimit.RateLimit)")
    public Object rateLimit(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        RateLimit rateLimit = signature.getMethod().getAnnotation(RateLimit.class);

        String identifier = resolveIdentifier(rateLimit.keyType());
        String endpointKey = signature.getDeclaringType().getSimpleName() + ":" + signature.getMethod().getName();
        String redisKey = KEY_PREFIX + identifier + ":" + endpointKey;

        RAtomicLong counter = redissonClient.getAtomicLong(redisKey);
        long count = counter.incrementAndGet();

        // 첫 번째 요청일 때만 TTL 설정 (윈도우 시작)
        if (count == 1) {
            counter.expire(rateLimit.windowSeconds(), TimeUnit.SECONDS);
        }

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
     * X-Real-IP (신뢰할 수 있는 프록시가 설정) → X-Forwarded-For 마지막 IP (가장 가까운 프록시) → RemoteAddr 순으로 사용.
     * X-Forwarded-For 첫 번째 값은 클라이언트가 임의로 조작 가능하므로 사용하지 않는다.
     */
    private String resolveClientIp() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = attrs.getRequest();

        // X-Real-IP: nginx 등 리버스 프록시가 설정한 단일 IP (스푸핑 불가)
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        // X-Forwarded-For: 프록시 체인에서 마지막(가장 가까운) 프록시가 추가한 IP 사용
        // 첫 번째 값은 클라이언트가 위조 가능하므로 마지막 값 사용
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] ips = forwarded.split(",");
            return ips[ips.length - 1].trim();
        }

        return request.getRemoteAddr();
    }
}
