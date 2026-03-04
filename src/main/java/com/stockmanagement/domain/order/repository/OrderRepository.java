package com.stockmanagement.domain.order.repository;

import com.stockmanagement.domain.order.entity.Order;
import com.stockmanagement.domain.order.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 주문 레포지토리.
 *
 * <p>멱등성 키 조회 및 항목을 fetch join한 단건 조회를 제공한다.
 */
public interface OrderRepository extends JpaRepository<Order, Long> {

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
}
