package com.stockmanagement.domain.refund.repository;

import com.stockmanagement.domain.refund.entity.Refund;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    Optional<Refund> findByPaymentId(Long paymentId);

    List<Refund> findAllByPaymentIdOrderByCreatedAtDesc(Long paymentId);

    /** 결제별 가장 최근 환불 조회 (부분 취소 여러 건 허용 후 requestRefund에서 사용). */
    Optional<Refund> findFirstByPaymentIdOrderByCreatedAtDesc(Long paymentId);

    Optional<Refund> findByOrderId(Long orderId);

    /** 특정 사용자의 환불 목록을 최신순으로 페이징 조회한다. */
    Page<Refund> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** 커서 기반 조회 — 첫 페이지. */
    List<Refund> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

    /** 커서 기반 조회 — 다음 페이지 (id < lastId). */
    List<Refund> findByUserIdAndIdLessThanOrderByIdDesc(Long userId, Long lastId, Pageable pageable);
}
