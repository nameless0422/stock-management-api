package com.stockmanagement.domain.point.repository;

import com.stockmanagement.domain.point.entity.PointTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {

    Page<PointTransaction> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<PointTransaction> findByOrderId(Long orderId);
}
