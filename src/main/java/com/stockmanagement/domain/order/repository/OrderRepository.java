package com.stockmanagement.domain.order.repository;

import com.stockmanagement.domain.order.entity.Order;
import com.stockmanagement.domain.order.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
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
     * 주문과 항목을 비관적 락(SELECT ... FOR UPDATE)으로 조회한다.
     * 상태 변이 메서드(cancel/confirm/refund)에서 사용하여
     * 만료 스케줄러와 결제 확정이 동시에 동일 주문을 수정하는 경쟁 조건을 방지한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("SELECT o FROM Order o JOIN FETCH o.items i JOIN FETCH i.product WHERE o.id = :id")
    Optional<Order> findByIdWithItemsForUpdate(@Param("id") Long id);

    /**
     * 특정 사용자의 주문 목록을 페이징 조회한다.
     * items는 별도 조회되므로 목록에서는 지연 로딩이 발생하지 않도록 주의한다.
     */
    Page<Order> findByUserId(Long userId, Pageable pageable);

    /** 여러 주문을 항목+상품 포함하여 한 번에 조회한다 (결제 목록 주문 요약용). */
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items i LEFT JOIN FETCH i.product WHERE o.id IN :ids")
    List<Order> findByIdsWithItems(@Param("ids") List<Long> ids);

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
     * 상태별 주문 수·금액을 단일 GROUP BY 쿼리로 집계한다 (대시보드 최적화).
     *
     * <p>기존 6개 개별 쿼리(count×4 + sum×1 + userCount×1) 중 5개를 대체한다.
     * 결과를 Map으로 변환하면 PENDING/CONFIRMED/CANCELLED 건수와 CONFIRMED 매출을 한 번에 얻을 수 있다.
     */
    @Query("SELECT o.status AS status, COUNT(o) AS orderCount, " +
           "COALESCE(SUM(o.totalAmount), 0) AS totalAmount " +
           "FROM Order o GROUP BY o.status")
    List<OrderStatsProjection> findOrderStats();

    /**
     * 기준 시각 이전에 생성된 PENDING 주문 ID 목록을 반환한다 (만료 주문 자동 취소용).
     *
     * @param threshold 기준 시각 (현재 시각 - 만료 시간)
     */
    @Query("SELECT o.id FROM Order o WHERE o.status = com.stockmanagement.domain.order.entity.OrderStatus.PENDING AND o.createdAt < :threshold")
    List<Long> findExpiredPendingOrderIds(@Param("threshold") LocalDateTime threshold);

    /** 회원 탈퇴 시 재고 예약 누수 방지를 위해 사용자의 PENDING 주문 ID 목록을 반환한다. */
    @Query("SELECT o.id FROM Order o WHERE o.userId = :userId AND o.status = com.stockmanagement.domain.order.entity.OrderStatus.PENDING")
    List<Long> findPendingIdsByUserId(@Param("userId") Long userId);

    /**
     * 기준 시각 이전에 PAYMENT_IN_PROGRESS 상태로 머무른 주문 ID 목록을 반환한다.
     *
     * <p>결제 처리 중 오류로 {@code resetOrderOnPaymentError()}가 실패한 주문을
     * 주기적으로 PENDING으로 복원하여 만료 스케줄러가 정리할 수 있도록 한다.
     * updatedAt 기준으로 조회하여 최근에 PAYMENT_IN_PROGRESS로 전환된 주문을 제외한다.
     *
     * @param threshold 기준 시각 (현재 시각 - 허용 대기 시간)
     */
    @Query("SELECT o.id FROM Order o WHERE o.status = com.stockmanagement.domain.order.entity.OrderStatus.PAYMENT_IN_PROGRESS AND o.updatedAt < :threshold")
    List<Long> findStuckPaymentInProgressOrderIds(@Param("threshold") LocalDateTime threshold);

    /**
     * 주문 소유자의 userId만 조회한다 (소유권 검증 전용 — Order 전체 로드 불필요).
     *
     * @param id 주문 ID
     * @return 주문 소유자 userId (주문 없으면 Optional.empty())
     */
    @Query("SELECT o.userId FROM Order o WHERE o.id = :id")
    Optional<Long> findUserIdById(@Param("id") Long id);

    /**
     * 사용자가 특정 상품을 구매(CONFIRMED 주문 보유)했는지 확인한다 (리뷰 작성 자격 검증).
     */
    @Query("SELECT COUNT(o) > 0 FROM Order o JOIN o.items i " +
           "WHERE o.userId = :userId AND i.product.id = :productId " +
           "AND o.status = com.stockmanagement.domain.order.entity.OrderStatus.CONFIRMED")
    boolean existsPurchaseByUserIdAndProductId(@Param("userId") Long userId,
                                               @Param("productId") Long productId);

    // ===== 일별 통계 집계 (DailyOrderStatsScheduler) =====

    /** 기간 내 전체 주문 수 (>= start AND < end) */
    @Query("SELECT COUNT(o) FROM Order o WHERE o.createdAt >= :start AND o.createdAt < :end")
    long countByCreatedAtBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 기간 내 특정 상태 주문 수 */
    @Query("SELECT COUNT(o) FROM Order o WHERE o.status = :status AND o.createdAt >= :start AND o.createdAt < :end")
    long countByStatusAndCreatedAtBetween(@Param("status") OrderStatus status,
                                          @Param("start") LocalDateTime start,
                                          @Param("end") LocalDateTime end);

    /** 기간 내 CONFIRMED 주문의 실결제 매출액 합계 (totalAmount - discountAmount - usedPoints) */
    @Query("SELECT COALESCE(SUM(o.totalAmount - o.discountAmount - o.usedPoints), 0) FROM Order o " +
           "WHERE o.status = com.stockmanagement.domain.order.entity.OrderStatus.CONFIRMED " +
           "AND o.createdAt >= :start AND o.createdAt < :end")
    BigDecimal sumRevenueByCreatedAtBetween(@Param("start") LocalDateTime start,
                                            @Param("end") LocalDateTime end);

    /**
     * 기간 내 주문을 상태별로 GROUP BY하여 건수·금액을 단일 쿼리로 집계한다 (일별 통계 최적화).
     *
     * <p>기존 4개 개별 쿼리(count×3 + sum×1)를 1개로 대체한다.
     * 결과를 status 키로 매핑하면 CONFIRMED/CANCELLED 건수와 매출액을 한 번에 얻을 수 있다.
     */
    @Query("SELECT o.status AS status, COUNT(o) AS orderCount, " +
           "COALESCE(SUM(CASE WHEN o.status = com.stockmanagement.domain.order.entity.OrderStatus.CONFIRMED " +
           "THEN o.totalAmount - o.discountAmount - o.usedPoints ELSE 0 END), 0) AS totalAmount " +
           "FROM Order o " +
           "WHERE o.createdAt >= :start AND o.createdAt < :end " +
           "GROUP BY o.status")
    List<OrderStatsProjection> findOrderStatsBetween(@Param("start") LocalDateTime start,
                                                     @Param("end") LocalDateTime end);
}
