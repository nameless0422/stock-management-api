package com.stockmanagement.domain.order.repository;

import com.stockmanagement.domain.order.entity.OrderDeliverySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderDeliverySnapshotRepository extends JpaRepository<OrderDeliverySnapshot, Long> {

    Optional<OrderDeliverySnapshot> findByOrderId(Long orderId);

    /** 여러 주문의 배송지 스냅샷을 한 번에 조회한다 (목록 조회 N+1 방지). */
    List<OrderDeliverySnapshot> findByOrderIdIn(List<Long> orderIds);
}
