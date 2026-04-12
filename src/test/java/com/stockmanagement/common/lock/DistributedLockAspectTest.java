package com.stockmanagement.common.lock;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DistributedLockAspect 단위 테스트")
class DistributedLockAspectTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock rLock;

    @InjectMocks
    private DistributedLockAspect aspect;

    private ProceedingJoinPoint joinPoint;
    private MethodSignature signature;

    /** @DistributedLock 어노테이션을 가진 테스트용 메서드 보유 클래스 */
    static class TestTarget {
        @DistributedLock(key = "'inventory:' + #productId")
        public String doWork(Long productId) {
            return "result";
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        Method method = TestTarget.class.getMethod("doWork", Long.class);

        joinPoint = mock(ProceedingJoinPoint.class);
        signature = mock(MethodSignature.class);

        given(joinPoint.getSignature()).willReturn(signature);
        given(signature.getMethod()).willReturn(method);
        given(signature.getParameterNames()).willReturn(new String[]{"productId"});
        given(joinPoint.getArgs()).willReturn(new Object[]{1L});
        given(redissonClient.getLock(anyString())).willReturn(rLock);
    }

    @Nested
    @DisplayName("락 획득 성공")
    class LockAcquired {

        @Test
        @DisplayName("락 획득 후 메서드를 실행하고 락을 해제한다")
        void proceedsAndReleasesLock() throws Throwable {
            given(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(true);
            given(rLock.isHeldByCurrentThread()).willReturn(true);
            given(joinPoint.proceed()).willReturn("result");

            Object result = aspect.lock(joinPoint);

            assertThat(result).isEqualTo("result");
            verify(rLock).unlock();
        }

        @Test
        @DisplayName("올바른 락 키(lock:inventory:{id})로 getLock을 호출한다")
        void usesCorrectLockKey() throws Throwable {
            given(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(true);
            given(rLock.isHeldByCurrentThread()).willReturn(true);
            given(joinPoint.proceed()).willReturn(null);

            aspect.lock(joinPoint);

            verify(redissonClient).getLock("lock:inventory:1");
        }

        @Test
        @DisplayName("메서드가 예외를 던져도 락을 해제한다")
        void releasesLockEvenOnException() throws Throwable {
            given(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(true);
            given(rLock.isHeldByCurrentThread()).willReturn(true);
            given(joinPoint.proceed()).willThrow(new RuntimeException("business error"));

            assertThatThrownBy(() -> aspect.lock(joinPoint))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("business error");

            verify(rLock).unlock();
        }
    }

    @Nested
    @DisplayName("락 획득 실패")
    class LockNotAcquired {

        @Test
        @DisplayName("tryLock 실패 시 LOCK_ACQUISITION_FAILED 예외를 발생시킨다")
        void throwsWhenLockNotAcquired() throws InterruptedException {
            given(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(false);

            assertThatThrownBy(() -> aspect.lock(joinPoint))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.LOCK_ACQUISITION_FAILED));

            verify(rLock, never()).unlock();
        }
    }
}
