package com.stockmanagement.domain.payment.repository;

import com.stockmanagement.domain.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository for {@link Payment} entities.
 */
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** Finds a payment by TossPayments-assigned paymentKey. Used in cancel and query flows. */
    Optional<Payment> findByPaymentKey(String paymentKey);

    /** Finds a payment by the tossOrderId we sent to TossPayments. Used in confirm flow. */
    Optional<Payment> findByTossOrderId(String tossOrderId);

    /** Finds a payment by our internal order ID. Used to check for existing PENDING payment. */
    Optional<Payment> findByOrderId(Long orderId);
}
