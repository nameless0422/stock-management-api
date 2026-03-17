package com.stockmanagement.domain.order.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 주문 상태 변경 이력 엔티티.
 *
 * <p>주문 생성·취소·확정·환불 시 상태 전이를 기록한다.
 * {@code fromStatus}는 주문 생성 시 null (최초 상태 기록).
 */
@Entity
@Table(name = "order_status_history",
        indexes = @Index(name = "idx_osh_order_id", columnList = "order_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    /** 이전 상태 — 주문 생성 시 null */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private OrderStatus fromStatus;

    /** 변경 후 상태 */
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private OrderStatus toStatus;

    /** 변경 주체 (username) */
    @Column(name = "changed_by", length = 100)
    private String changedBy;

    /** 변경 사유 (선택) */
    @Column(length = 255)
    private String note;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private OrderStatusHistory(Long orderId, OrderStatus fromStatus, OrderStatus toStatus,
                               String changedBy, String note) {
        this.orderId   = orderId;
        this.fromStatus = fromStatus;
        this.toStatus   = toStatus;
        this.changedBy  = changedBy;
        this.note       = note;
    }
}
