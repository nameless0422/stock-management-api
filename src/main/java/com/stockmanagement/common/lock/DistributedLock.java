package com.stockmanagement.common.lock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 분산 락 적용 어노테이션.
 *
 * <p>{@link DistributedLockAspect}가 이 어노테이션을 감지하여
 * Redisson 분산 락을 획득·해제한다.
 *
 * <p>key는 SpEL 표현식으로 작성하며, 메서드 파라미터를 {@code #paramName} 형식으로 참조한다.
 * 예: {@code "'inventory:' + #productId"}
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

    /** SpEL 표현식으로 작성하는 락 키 (예: {@code "'inventory:' + #productId"}) */
    String key();

    /** 락 획득 대기 최대 시간 (기본 5초) */
    long waitTime() default 5;

    /** 락 보유 최대 시간 — 이 시간이 지나면 자동 해제 (기본 3초) */
    long leaseTime() default 3;

    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * 락 획득 실패 시 예외를 던지지 않고 메서드 실행을 건너뛴다.
     * 스케줄러처럼 "이번 주기는 스킵"이 허용되는 경우에 사용한다.
     */
    boolean skipOnFailure() default false;
}
