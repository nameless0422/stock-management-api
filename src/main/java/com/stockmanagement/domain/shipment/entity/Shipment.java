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
 * 상태 전이: PREPARING → SHIPPED → DELIVERED / RETURNED
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
    @Column(nullable = false, length = 20)
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
     * @throws BusinessException DELIVERED·RETURNED 상태에서 반품 시도 시
     */
    public void processReturn() {
        if (this.status == ShipmentStatus.DELIVERED || this.status == ShipmentStatus.RETURNED) {
            throw new BusinessException(ErrorCode.INVALID_SHIPMENT_STATUS);
        }
        this.status = ShipmentStatus.RETURNED;
    }
}
