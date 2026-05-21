package com.stockmanagement.domain.point.repository;

import com.stockmanagement.domain.point.entity.PointTransaction;
import com.stockmanagement.domain.point.entity.PointTransactionStatus;
import com.stockmanagement.domain.point.entity.PointTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {

    Page<PointTransaction> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<PointTransaction> findByOrderId(Long orderId);

    /** Outbox 재처리 시 포인트 이중 적립 방지용 멱등성 체크 */
    boolean existsByOrderIdAndType(Long orderId, PointTransactionType type);

    /** 특정 주문의 PENDING 적립 트랜잭션 조회 */
    Optional<PointTransaction> findByOrderIdAndTypeAndStatus(
            Long orderId, PointTransactionType type, PointTransactionStatus status);

    /** 사용자의 적립 예정 (PENDING) 트랜잭션 페이징 조회 */
    Page<PointTransaction> findByUserIdAndStatusOrderByCreatedAtDesc(
            Long userId, PointTransactionStatus status, Pageable pageable);
}
