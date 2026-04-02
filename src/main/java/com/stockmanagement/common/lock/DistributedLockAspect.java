package com.stockmanagement.common.lock;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

/**
 * {@link DistributedLock} 어노테이션을 처리하는 AOP 어스펙트.
 *
 * <p>{@code @Order(Ordered.HIGHEST_PRECEDENCE)}를 지정해 {@code @Transactional}보다
 * 먼저 실행되도록 보장한다.
 * 실행 순서: 락 획득 → 트랜잭션 시작 → 메서드 실행 → 트랜잭션 커밋 → 락 해제
 */
@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class DistributedLockAspect {

    private static final String LOCK_KEY_PREFIX = "lock:";

    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final RedissonClient redissonClient;

    @Around("@annotation(com.stockmanagement.common.lock.DistributedLock)")
    public Object lock(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        DistributedLock distributedLock = signature.getMethod().getAnnotation(DistributedLock.class);
        String lockKey = LOCK_KEY_PREFIX + resolveKey(joinPoint, distributedLock.key());
        RLock lock = redissonClient.getLock(lockKey);

        boolean acquired = false;
        try {
            acquired = lock.tryLock(
                    distributedLock.waitTime(),
                    distributedLock.leaseTime(),
                    distributedLock.timeUnit()
            );
            if (!acquired) {
                if (distributedLock.skipOnFailure()) {
                    log.debug("분산 락 획득 실패 (스킵): key={}", lockKey);
                    return null;
                }
                log.warn("분산 락 획득 실패: key={}", lockKey);
                throw new BusinessException(ErrorCode.LOCK_ACQUISITION_FAILED);
            }
            log.debug("분산 락 획득: key={}", lockKey);
            return joinPoint.proceed();
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("분산 락 해제: key={}", lockKey);
            }
        }
    }

    /**
     * SpEL 표현식을 메서드 파라미터 컨텍스트로 평가하여 락 키를 반환한다.
     */
    private String resolveKey(ProceedingJoinPoint joinPoint, String keyExpression) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        EvaluationContext context = new StandardEvaluationContext();
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }
        return parser.parseExpression(keyExpression).getValue(context, String.class);
    }
}
