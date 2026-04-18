package com.stockmanagement.domain.refund.repository;

import com.stockmanagement.domain.refund.entity.Refund;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    Optional<Refund> findByPaymentId(Long paymentId);

    Optional<Refund> findByOrderId(Long orderId);

    /** 특정 사용자의 환불 목록을 최신순으로 페이징 조회한다. */
    Page<Refund> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
