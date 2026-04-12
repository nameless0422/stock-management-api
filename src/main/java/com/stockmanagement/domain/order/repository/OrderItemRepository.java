package com.stockmanagement.domain.order.repository;

import com.stockmanagement.domain.order.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 주문 항목 레포지토리.
 *
 * <p>현재는 기본 CRUD 외 특별한 쿼리가 없다.
 * Order → OrderItem cascade로 대부분의 작업이 처리되므로
 * 직접 사용 빈도는 낮다.
 */
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    /** 특정 주문의 모든 항목 조회 */
    List<OrderItem> findByOrderId(Long orderId);
}
