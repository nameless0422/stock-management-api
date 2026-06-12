package com.stockmanagement.domain.shipment.entity;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 배송 엔티티.
 *
 * <p>주문 1건당 배송 1건(order_id UNIQUE)으로 관리된다.
 * 상태 전이: PREPARING → SHIPPED → DELIVERED → RETURN_REQUESTED → RETURNED
 *
 * <p>설계 원칙:
 * <ul>
 *   <li>public setter 없음 — 상태 변경은 비즈니스 메서드로만 허용
 *   <li>{@code orderId}는 orders.id FK로 연결
 * </ul>
 */
@Entity
@Table(name = "shipments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 연결된 주문 ID — UNIQUE (주문 1건당 배송 1건) */
    @Column(nullable = false, unique = true)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ShipmentStatus status;

    /** 택배사명 (예: CJ대한통운, 한진택배) */
    @Column(length = 100)
    private String carrier;

    /** 운송장 번호 */
    @Column(length = 100)
    private String trackingNumber;

    /** 실제 출고 일시 */
    private LocalDateTime shippedAt;

    /** 배송 완료 일시 */
    private LocalDateTime deliveredAt;

    /** 택배사 예상 도착일 (출고 시 입력, 선택값) */
    @Column(name = "estimated_delivery_at")
    private LocalDate estimatedDeliveryAt;

    /** 반품 사유 (사용자 입력) */
    @Column(name = "return_reason", length = 500)
    private String returnReason;

    /** 반품 신청 일시 */
    @Column(name = "return_requested_at")
    private LocalDateTime returnRequestedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private Shipment(Long orderId) {
        this.orderId = orderId;
        this.status = ShipmentStatus.PREPARING;
    }

    // ===== 비즈니스 메서드 =====

    /**
     * 배송을 출고한다. (PREPARING → SHIPPED)
     *
     * @throws BusinessException PREPARING 상태가 아닌 경우
     */
    public void ship(String carrier, String trackingNumber, LocalDate estimatedDeliveryAt) {
        if (this.status != ShipmentStatus.PREPARING) {
            throw new BusinessException(ErrorCode.INVALID_SHIPMENT_STATUS);
        }
        this.status = ShipmentStatus.SHIPPED;
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
        this.estimatedDeliveryAt = estimatedDeliveryAt;
        this.shippedAt = LocalDateTime.now();
    }

    /**
     * 배송을 완료 처리한다. (SHIPPED → DELIVERED)
     *
     * @throws BusinessException SHIPPED 상태가 아닌 경우
     */
    public void deliver() {
        if (this.status != ShipmentStatus.SHIPPED) {
            throw new BusinessException(ErrorCode.INVALID_SHIPMENT_STATUS);
        }
        this.status = ShipmentStatus.DELIVERED;
        this.deliveredAt = LocalDateTime.now();
    }

    /**
     * 반품 처리한다. (PREPARING|SHIPPED → RETURNED)
     *
     * @throws BusinessException DELIVERED·RETURNED·RETURN_REQUESTED 상태에서 반품 시도 시
     */
    public void processReturn() {
        if (this.status == ShipmentStatus.DELIVERED
                || this.status == ShipmentStatus.RETURNED
                || this.status == ShipmentStatus.RETURN_REQUESTED) {
            throw new BusinessException(ErrorCode.INVALID_SHIPMENT_STATUS);
        }
        this.status = ShipmentStatus.RETURNED;
    }

    /**
     * 사용자가 반품을 신청한다. (DELIVERED → RETURN_REQUESTED)
     *
     * @throws BusinessException DELIVERED 상태가 아닌 경우
     * @throws BusinessException 이미 반품 신청된 경우 (RETURN_REQUESTED)
     */
    public void requestReturn(String reason) {
        if (this.status == ShipmentStatus.RETURN_REQUESTED) {
            throw new BusinessException(ErrorCode.RETURN_ALREADY_REQUESTED);
        }
        if (this.status != ShipmentStatus.DELIVERED) {
            throw new BusinessException(ErrorCode.INVALID_SHIPMENT_STATUS);
        }
        this.status = ShipmentStatus.RETURN_REQUESTED;
        this.returnReason = reason;
        this.returnRequestedAt = LocalDateTime.now();
    }

    /**
     * ADMIN이 반품을 승인한다. (RETURN_REQUESTED → RETURNED)
     *
     * @throws BusinessException RETURN_REQUESTED 상태가 아닌 경우
     */
    public void approveReturn() {
        if (this.status != ShipmentStatus.RETURN_REQUESTED) {
            throw new BusinessException(ErrorCode.INVALID_SHIPMENT_STATUS);
        }
        this.status = ShipmentStatus.RETURNED;
    }

    /**
     * ADMIN이 반품을 거부한다. (RETURN_REQUESTED → DELIVERED)
     *
     * @throws BusinessException RETURN_REQUESTED 상태가 아닌 경우
     */
    public void rejectReturn() {
        if (this.status != ShipmentStatus.RETURN_REQUESTED) {
            throw new BusinessException(ErrorCode.INVALID_SHIPMENT_STATUS);
        }
        this.status = ShipmentStatus.DELIVERED;
        this.returnReason = null;
        this.returnRequestedAt = null;
    }
}
