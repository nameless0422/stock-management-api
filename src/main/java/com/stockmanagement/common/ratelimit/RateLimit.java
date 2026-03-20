package com.stockmanagement.common.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * API Rate Limit 어노테이션.
 *
 * <p>{@link RateLimitAspect}가 이 어노테이션을 감지하여 Redis 카운터로 요청 횟수를 추적한다.
 * 한도 초과 시 {@code 429 Too Many Requests} 응답을 반환한다.
 *
 * <ul>
 *   <li>USER: 인증된 사용자 기준 (미인증 시 IP로 폴백)
 *   <li>IP: 클라이언트 IP 기준 (X-Forwarded-For 헤더 우선)
 * </ul>
 *
 * <p>키 형식: {@code rate-limit:api:{user_or_ip}:{ClassName}:{methodName}}
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 시간 윈도우 내 허용 요청 횟수 */
    int limit() default 10;

    /** 시간 윈도우 (초 단위) */
    long windowSeconds() default 60;

    /** 요청 식별 기준 */
    KeyType keyType() default KeyType.USER;

    enum KeyType {
        /** 인증된 사용자명 기준 (미인증 시 IP 폴백) */
        USER,
        /** 클라이언트 IP 기준 */
        IP
    }
}
