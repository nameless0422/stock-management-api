package com.stockmanagement.domain.order.repository;

import com.stockmanagement.domain.order.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /** 결제 완료된 주문에서 특정 상품의 누적 판매 수량 */
    @Query("SELECT COALESCE(SUM(oi.quantity), 0) FROM OrderItem oi " +
           "WHERE oi.product.id = :productId " +
           "AND oi.order.status IN (com.stockmanagement.domain.order.entity.OrderStatus.CONFIRMED, " +
           "com.stockmanagement.domain.order.entity.OrderStatus.CANCEL_IN_PROGRESS)")
    long sumSalesCountByProductId(@Param("productId") Long productId);

    /** 결제 완료된 주문에서 상품별 누적 판매 수량 배치 조회 — [productId, sum] 배열 목록 (재색인용) */
    @Query("SELECT oi.product.id, COALESCE(SUM(oi.quantity), 0) FROM OrderItem oi " +
           "WHERE oi.product.id IN :productIds " +
           "AND oi.order.status IN (com.stockmanagement.domain.order.entity.OrderStatus.CONFIRMED, " +
           "com.stockmanagement.domain.order.entity.OrderStatus.CANCEL_IN_PROGRESS) " +
           "GROUP BY oi.product.id")
    List<Object[]> sumSalesCountByProductIdIn(@Param("productIds") List<Long> productIds);
}
