package com.stockmanagement.domain.payment.repository;

import com.stockmanagement.domain.payment.entity.Payment;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Repository for {@link Payment} entities.
 */
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** Finds a payment by TossPayments-assigned paymentKey. Used in cancel and query flows. */
    Optional<Payment> findByPaymentKey(String paymentKey);

    /** Finds a payment by the tossOrderId we sent to TossPayments. Used in confirm flow. */
    Optional<Payment> findByTossOrderId(String tossOrderId);

    /**
     * Redis 장애 등으로 분산 락이 우회된 경우에도 DB 레벨에서 중복 confirm을 방지하기 위한
     * 비관적 락 조회. confirm() 흐름에서만 사용한다.
     *
     * <p>lock timeout 3초: 교착 상태 방지. 초과 시 LockTimeoutException → PAYMENT_PROCESSING_IN_PROGRESS
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("SELECT p FROM Payment p WHERE p.tossOrderId = :tossOrderId")
    Optional<Payment> findByTossOrderIdWithLock(@Param("tossOrderId") String tossOrderId);

    /**
     * cancel() 흐름에서 비관적 락으로 payment 행을 잠근다.
     * Redis 장애 등으로 분산 락이 우회된 경우에도 DB 레벨에서 중복 취소를 방지한다.
     *
     * <p>lock timeout 3초: 교착 상태 방지. 초과 시 LockTimeoutException → PAYMENT_PROCESSING_IN_PROGRESS
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("SELECT p FROM Payment p WHERE p.paymentKey = :paymentKey")
    Optional<Payment> findByPaymentKeyWithLock(@Param("paymentKey") String paymentKey);

    /** Finds a payment by our internal order ID. Used to check for existing PENDING payment. */
    Optional<Payment> findByOrderId(Long orderId);
}
