package com.stockmanagement.domain.point.repository;

import com.stockmanagement.domain.point.entity.PointTransaction;
import com.stockmanagement.domain.point.entity.PointTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {

    Page<PointTransaction> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<PointTransaction> findByOrderId(Long orderId);

    /** Outbox 재처리 시 포인트 이중 적립 방지용 멱등성 체크 */
    boolean existsByOrderIdAndType(Long orderId, PointTransactionType type);
}
