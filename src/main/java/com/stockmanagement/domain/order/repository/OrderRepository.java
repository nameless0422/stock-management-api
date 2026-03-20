package com.stockmanagement.domain.order.repository;

import com.stockmanagement.domain.order.entity.Order;
import com.stockmanagement.domain.order.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 주문 레포지토리.
 *
 * <p>멱등성 키 조회 및 항목을 fetch join한 단건 조회를 제공한다.
 */
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    /**
     * 멱등성 키로 기존 주문을 조회한다.
     * 동일 키로 재요청이 오면 새 주문 생성 없이 기존 주문을 반환한다.
     */
    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    /**
     * 주문과 항목을 한 번의 쿼리로 조회한다 (N+1 방지).
     * 단건 상세 조회 시 사용한다.
     */
    @Query("SELECT o FROM Order o JOIN FETCH o.items i JOIN FETCH i.product WHERE o.id = :id")
    Optional<Order> findByIdWithItems(@Param("id") Long id);

    /**
     * 특정 사용자의 주문 목록을 페이징 조회한다.
     * items는 별도 조회되므로 목록에서는 지연 로딩이 발생하지 않도록 주의한다.
     */
    Page<Order> findByUserId(Long userId, Pageable pageable);

    /** 주문 목록 전체 페이징 조회 (관리자용) */
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    /** 특정 사용자 + 상태 조합 조회 (관리자용) */
    Page<Order> findByUserIdAndStatus(Long userId, OrderStatus status, Pageable pageable);

    /** 상태별 주문 수 집계 (대시보드용) */
    long countByStatus(OrderStatus status);

    /** 특정 상태의 주문 총 매출액 (대시보드용) */
    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.status = :status")
    BigDecimal sumTotalAmountByStatus(@Param("status") OrderStatus status);

    /**
     * 기준 시각 이전에 생성된 PENDING 주문 ID 목록을 반환한다 (만료 주문 자동 취소용).
     *
     * @param threshold 기준 시각 (현재 시각 - 만료 시간)
     */
    @Query("SELECT o.id FROM Order o WHERE o.status = com.stockmanagement.domain.order.entity.OrderStatus.PENDING AND o.createdAt < :threshold")
    List<Long> findExpiredPendingOrderIds(@Param("threshold") LocalDateTime threshold);

    // ===== 일별 통계 집계 (DailyOrderStatsScheduler) =====

    /** 기간 내 전체 주문 수 */
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    /** 기간 내 특정 상태 주문 수 */
    @Query("SELECT COUNT(o) FROM Order o WHERE o.status = :status AND o.createdAt >= :start AND o.createdAt < :end")
    long countByStatusAndCreatedAtBetween(@Param("status") OrderStatus status,
                                          @Param("start") LocalDateTime start,
                                          @Param("end") LocalDateTime end);

    /** 기간 내 CONFIRMED 주문의 매출액 합계 (totalAmount - discountAmount) */
    @Query("SELECT COALESCE(SUM(o.totalAmount - o.discountAmount), 0) FROM Order o " +
           "WHERE o.status = com.stockmanagement.domain.order.entity.OrderStatus.CONFIRMED " +
           "AND o.createdAt >= :start AND o.createdAt < :end")
    BigDecimal sumRevenueByCreatedAtBetween(@Param("start") LocalDateTime start,
                                            @Param("end") LocalDateTime end);
}
