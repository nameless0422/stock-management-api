package com.stockmanagement.domain.order.repository;

import com.stockmanagement.domain.order.entity.OrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {

    /** 특정 주문의 상태 이력을 시간순(오래된 것부터)으로 조회한다. */
    List<OrderStatusHistory> findByOrderIdOrderByCreatedAtAsc(Long orderId);
}
